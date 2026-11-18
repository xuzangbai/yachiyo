#define COBJMACROS
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <d3d11.h>
#include <mfapi.h>
#include <mferror.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    UINT32 coded_width, coded_height, width, height, x, y, pitch;
} FrameLayout;

typedef struct {
    IMFSourceReader *reader;
    ID3D11Device *device;
    IMFDXGIDeviceManager *manager;
    UINT32 width, height, stride;
    FrameLayout layout;
    BYTE *scratch;
    DWORD scratch_size;
} Video;

static void clear_reader(Video *video) {
    if (video->reader) IMFSourceReader_Release(video->reader);
    if (video->manager) IMFDXGIDeviceManager_Release(video->manager);
    if (video->device) ID3D11Device_Release(video->device);
    video->reader = NULL;
    video->manager = NULL;
    video->device = NULL;
}

static void fail(JNIEnv *env, const char *operation, HRESULT hr) {
    char message[128];
    snprintf(message, sizeof(message), "%s (HRESULT 0x%08lx)", operation, (unsigned long)hr);
    jclass exception = (*env)->FindClass(env, "java/io/IOException");
    if (exception) (*env)->ThrowNew(env, exception, message);
}

static BOOL frame_layout(IMFMediaType *type, FrameLayout *layout) {
    UINT64 size;
    if (FAILED(IMFMediaType_GetUINT64(type, &MF_MT_FRAME_SIZE, &size))) return FALSE;
    *layout = (FrameLayout){0};
    layout->width = layout->coded_width = (UINT32)(size >> 32);
    layout->height = layout->coded_height = (UINT32)size;
    IMFMediaType_GetUINT32(type, &MF_MT_DEFAULT_STRIDE, &layout->pitch);
    MFVideoArea aperture = {0};
    if (FAILED(IMFMediaType_GetBlob(type, &MF_MT_MINIMUM_DISPLAY_APERTURE, (BYTE *)&aperture, sizeof(aperture), NULL))) {
        IMFMediaType_GetBlob(type, &MF_MT_GEOMETRIC_APERTURE, (BYTE *)&aperture, sizeof(aperture), NULL);
    }
    if (aperture.Area.cx > 0 && aperture.Area.cy > 0) {
        if (aperture.OffsetX.value < 0 || aperture.OffsetY.value < 0 || aperture.OffsetX.fract || aperture.OffsetY.fract) return FALSE;
        layout->x = aperture.OffsetX.value;
        layout->y = aperture.OffsetY.value;
        layout->width = aperture.Area.cx;
        layout->height = aperture.Area.cy;
    }
    return layout->width && layout->height && layout->coded_width <= 8192 && layout->coded_height <= 8192
        && layout->x + layout->width <= layout->coded_width && layout->y + layout->height <= layout->coded_height
        && !((layout->width | layout->height | layout->x | layout->y | layout->coded_width | layout->coded_height) & 1);
}

static HRESULT configure_reader(Video *video, const WCHAR *path, BOOL d3d, jint *info) {
    IMFAttributes *attributes = NULL;
    IMFMediaType *output = NULL, *current = NULL, *native = NULL;
    HRESULT hr = MFCreateAttributes(&attributes, 3);
    if (FAILED(hr)) goto done;
    if (d3d) {
        UINT token;
        hr = D3D11CreateDevice(NULL, D3D_DRIVER_TYPE_HARDWARE, NULL, D3D11_CREATE_DEVICE_VIDEO_SUPPORT | D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                               NULL, 0, D3D11_SDK_VERSION, &video->device, NULL, NULL);
        if (FAILED(hr)) goto done;
        ID3D10Multithread *multithread = NULL;
        if (SUCCEEDED(ID3D11Device_QueryInterface(video->device, &IID_ID3D10Multithread, (void **)&multithread))) {
            ID3D10Multithread_SetMultithreadProtected(multithread, TRUE);
            ID3D10Multithread_Release(multithread);
        }
        hr = MFCreateDXGIDeviceManager(&token, &video->manager);
        if (FAILED(hr)) goto done;
        hr = IMFDXGIDeviceManager_ResetDevice(video->manager, (IUnknown *)video->device, token);
        if (FAILED(hr)) goto done;
        hr = IMFAttributes_SetUnknown(attributes, &MF_SOURCE_READER_D3D_MANAGER, (IUnknown *)video->manager);
        if (FAILED(hr)) goto done;
        hr = IMFAttributes_SetUINT32(attributes, &MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, 1);
        if (FAILED(hr)) goto done;
    }
    hr = MFCreateSourceReaderFromURL(path, attributes, &video->reader);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetStreamSelection(video->reader, MF_SOURCE_READER_ALL_STREAMS, FALSE);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetStreamSelection(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, TRUE);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_GetNativeMediaType(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, 0, &native);
    if (FAILED(hr)) goto done;
    // Keep the original dimensions and timestamps. The requested subtype only selects the decoded pixel layout.
    hr = MFCreateMediaType(&output);
    if (FAILED(hr)) goto done;
    hr = IMFMediaType_SetGUID(output, &MF_MT_MAJOR_TYPE, &MFMediaType_Video);
    if (FAILED(hr)) goto done;
    hr = IMFMediaType_SetGUID(output, &MF_MT_SUBTYPE, &MFVideoFormat_NV12);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetCurrentMediaType(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, NULL, output);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_GetCurrentMediaType(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, &current);
    if (FAILED(hr)) goto done;
    UINT64 fps = 0, aspect = 0;
    FrameLayout input, decoded;
    if (!frame_layout(current, &decoded)) {
        hr = MF_E_INVALIDMEDIATYPE;
        goto done;
    }
    if (frame_layout(native, &input) && (input.width != decoded.width || input.height != decoded.height)) {
        hr = MF_E_INVALIDMEDIATYPE;
        goto done;
    }
    video->layout = decoded;
    video->width = decoded.width;
    video->height = decoded.height;
    video->stride = (video->width + 3) & ~3u;
    IMFMediaType_GetUINT64(native, &MF_MT_FRAME_RATE, &fps);
    IMFMediaType_GetUINT64(native, &MF_MT_PIXEL_ASPECT_RATIO, &aspect);
    UINT32 matrix = 0, range = 0, rotation = 0, source_stride = 0;
    if (FAILED(IMFMediaType_GetUINT32(current, &MF_MT_YUV_MATRIX, &matrix)) || !matrix)
        IMFMediaType_GetUINT32(native, &MF_MT_YUV_MATRIX, &matrix);
    if (FAILED(IMFMediaType_GetUINT32(current, &MF_MT_VIDEO_NOMINAL_RANGE, &range)) || !range)
        IMFMediaType_GetUINT32(native, &MF_MT_VIDEO_NOMINAL_RANGE, &range);
    IMFMediaType_GetUINT32(native, &MF_MT_VIDEO_ROTATION, &rotation);
    IMFMediaType_GetUINT32(current, &MF_MT_DEFAULT_STRIDE, &source_stride);
    UINT32 transfer = 0;
    IMFMediaType_GetUINT32(native, &MF_MT_TRANSFER_FUNCTION, &transfer);
    // This renderer displays SDR NV12. Leave a file with HDR transfer metadata intact and fall back.
    if (transfer == MFVideoTransFunc_2084 || transfer == MFVideoTransFunc_HLG) {
        hr = MF_E_INVALIDMEDIATYPE;
        goto done;
    }
    info[0] = video->width;
    info[1] = video->height;
    info[2] = (jint)(fps >> 32);
    info[3] = (jint)fps;
    info[4] = matrix == MFVideoTransferMatrix_BT601 ? 1 : matrix == MFVideoTransferMatrix_BT2020_10 || matrix == MFVideoTransferMatrix_BT2020_12 ? 2 : !matrix && video->height < 720 ? 1 : 0;
    info[5] = range == MFNominalRange_0_255;
    info[6] = d3d;
    info[7] = (jint)(aspect >> 32);
    info[8] = (jint)aspect;
    info[9] = (jint)rotation;
    info[10] = (jint)source_stride;
done:
    if (native) IMFMediaType_Release(native);
    if (current) IMFMediaType_Release(current);
    if (output) IMFMediaType_Release(output);
    if (attributes) IMFAttributes_Release(attributes);
    if (FAILED(hr)) clear_reader(video);
    return hr;
}

JNIEXPORT jlong JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsVideo_open0(JNIEnv *env, jclass type, jstring file, jintArray metadata, jboolean allow_d3d) {
    (void)type;
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) { fail(env, "Initialize Windows COM", hr); return 0; }
    hr = MFStartup(MF_VERSION, MFSTARTUP_FULL);
    if (FAILED(hr)) { CoUninitialize(); fail(env, "Initialize Media Foundation", hr); return 0; }
    Video *video = calloc(1, sizeof(*video));
    UINT32 length = (UINT32)(*env)->GetStringLength(env, file);
    WCHAR *path = calloc((size_t)length + 1, sizeof(*path));
    if (!video || !path) { hr = E_OUTOFMEMORY; goto error; }
    (*env)->GetStringRegion(env, file, 0, (jsize)length, (jchar *)path);
    jint info[11] = {0};
    hr = allow_d3d ? configure_reader(video, path, TRUE, info) : E_FAIL;
    if (FAILED(hr)) hr = configure_reader(video, path, FALSE, info);
    if (FAILED(hr)) goto error;
    free(path);
    (*env)->SetIntArrayRegion(env, metadata, 0, 11, info);
    return (jlong)(intptr_t)video;
error:
    free(path);
    free(video);
    MFShutdown();
    CoUninitialize();
    fail(env, "Open original video / NV12", hr);
    return 0;
}

static void copy_picture(Video *video, BYTE *destination, const BYTE *source, UINT32 pitch, UINT32 lines,
                         UINT32 x, UINT32 y) {
    if (video->width == video->stride && pitch == video->width && lines == video->height && !x && !y) {
        memcpy(destination, source, (size_t)video->width * (video->height + video->height / 2));
        return;
    }
    const BYTE *luma = source + (size_t)y * pitch + x;
    const BYTE *chroma = source + (size_t)(lines + y / 2) * pitch + x;
    for (UINT32 row = 0; row < video->height; ++row) {
        memcpy(destination + (size_t)row * video->stride, luma + (size_t)row * pitch, video->width);
    }
    destination += (size_t)video->height * video->stride;
    for (UINT32 row = 0; row < video->height / 2; ++row) {
        memcpy(destination + (size_t)row * video->stride, chroma + (size_t)row * pitch, video->width);
    }
}

static BOOL refresh_frame_layout(Video *video) {
    IMFMediaType *type = NULL;
    if (FAILED(IMFSourceReader_GetCurrentMediaType(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, &type))) return FALSE;
    GUID subtype;
    FrameLayout layout;
    BOOL same = SUCCEEDED(IMFMediaType_GetGUID(type, &MF_MT_SUBTYPE, &subtype)) && IsEqualGUID(&subtype, &MFVideoFormat_NV12)
        && frame_layout(type, &layout) && layout.width == video->width && layout.height == video->height;
    if (same) video->layout = layout;
    IMFMediaType_Release(type);
    return same;
}

static BOOL copy_2d(Video *video, IMF2DBuffer *planar, BYTE *destination) {
    DWORD length;
    if (FAILED(IMF2DBuffer_GetContiguousLength(planar, &length))) return FALSE;
    UINT32 rows = video->height + video->height / 2;
    if (video->stride == video->width && length == video->width * rows) {
        return SUCCEEDED(IMF2DBuffer_ContiguousCopyTo(planar, destination, length));
    }
    FrameLayout layout = video->layout;
    DWORD packed = layout.coded_width * (layout.coded_height + layout.coded_height / 2);
    if (length != packed) return FALSE;
    if (video->scratch_size < length) {
        BYTE *scratch = realloc(video->scratch, length);
        if (!scratch) return FALSE;
        video->scratch = scratch;
        video->scratch_size = length;
    }
    if (FAILED(IMF2DBuffer_ContiguousCopyTo(planar, video->scratch, length))) return FALSE;
    copy_picture(video, destination, video->scratch, layout.coded_width, layout.coded_height, layout.x, layout.y);
    return TRUE;
}

JNIEXPORT jlong JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsVideo_read0(JNIEnv *env, jclass type, jlong handle, jobject pixels, jint stride_hint) {
    (void)type;
    Video *video = (Video *)(intptr_t)handle;
    BYTE *destination = (*env)->GetDirectBufferAddress(env, pixels);
    DWORD rows = video->height + video->height / 2;
    if (!destination || (*env)->GetDirectBufferCapacity(env, pixels) < (jlong)video->stride * rows) {
        fail(env, "Invalid video frame buffer", E_INVALIDARG);
        return 0;
    }
    IMFSample *sample = NULL;
    LONGLONG timestamp = 0;
    HRESULT hr = S_OK;
    for (int attempt = 0; !sample && attempt < 32; ++attempt) {
        DWORD flags = 0;
        hr = IMFSourceReader_ReadSample(video->reader, MF_SOURCE_READER_FIRST_VIDEO_STREAM, 0, NULL, &flags, &timestamp, &sample);
        if (FAILED(hr)) break;
        if (flags & MF_SOURCE_READERF_ERROR) { hr = E_FAIL; break; }
        if ((flags & MF_SOURCE_READERF_CURRENTMEDIATYPECHANGED) && !refresh_frame_layout(video)) {
            hr = MF_E_INVALIDMEDIATYPE;
            break;
        }
        if (!sample && (flags & MF_SOURCE_READERF_ENDOFSTREAM)) return INT64_MIN;
    }
    IMFMediaBuffer *buffer = NULL;
    if (SUCCEEDED(hr)) hr = sample ? IMFSample_ConvertToContiguousBuffer(sample, &buffer) : E_FAIL;
    if (SUCCEEDED(hr)) {
        IMF2DBuffer *planar = NULL;
        BOOL copied = FALSE;
        if (SUCCEEDED(IMFMediaBuffer_QueryInterface(buffer, &IID_IMF2DBuffer, (void **)&planar))) {
            copied = copy_2d(video, planar, destination);
            IMF2DBuffer_Release(planar);
        }
        if (!copied) {
            BYTE *data = NULL;
            DWORD length = 0;
            hr = IMFMediaBuffer_Lock(buffer, &data, NULL, &length);
            if (SUCCEEDED(hr)) {
                FrameLayout layout = video->layout;
                UINT32 pitch = layout.coded_width;
                UINT32 coded_rows = layout.coded_height + layout.coded_height / 2;
                UINT32 hinted = layout.pitch ? layout.pitch : (UINT32)stride_hint;
                if (length != pitch * coded_rows && hinted >= pitch && (UINT64)hinted * coded_rows <= length) {
                    pitch = hinted;
                }
                if (data && length == video->width * rows) {
                    copy_picture(video, destination, data, video->width, video->height, 0, 0);
                } else if (data && (UINT64)pitch * coded_rows <= length) {
                    copy_picture(video, destination, data, pitch, layout.coded_height, layout.x, layout.y);
                } else {
                    hr = MF_E_BUFFERTOOSMALL;
                }
                IMFMediaBuffer_Unlock(buffer);
            }
        }
    }
    if (buffer) IMFMediaBuffer_Release(buffer);
    if (sample) IMFSample_Release(sample);
    if (FAILED(hr)) fail(env, "Decode original video frame", hr);
    return timestamp;
}

JNIEXPORT void JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsVideo_rewind0(JNIEnv *env, jclass type, jlong handle) {
    (void)type;
    Video *video = (Video *)(intptr_t)handle;
    PROPVARIANT time = {0};
    time.vt = VT_I8;
    HRESULT hr = IMFSourceReader_SetCurrentPosition(video->reader, &GUID_NULL, &time);
    if (FAILED(hr)) fail(env, "Seek to start of video", hr);
}

JNIEXPORT void JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsVideo_close0(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    Video *video = (Video *)(intptr_t)handle;
    clear_reader(video);
    free(video->scratch);
    free(video);
    MFShutdown();
    CoUninitialize();
}

typedef struct {
    IMFSourceReader *reader;
    IMFMediaBuffer *buffer;
    DWORD offset, block_align;
    BOOL eof;
} Audio;

static void clear_audio_sample(Audio *audio) {
    if (audio->buffer) IMFMediaBuffer_Release(audio->buffer);
    audio->buffer = NULL;
    audio->offset = 0;
}

static HRESULT configure_audio(Audio *audio, const WCHAR *path, jint *info) {
    IMFMediaType *request = NULL, *format = NULL;
    HRESULT hr = MFCreateSourceReaderFromURL(path, NULL, &audio->reader);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetStreamSelection(audio->reader, MF_SOURCE_READER_ALL_STREAMS, FALSE);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetStreamSelection(audio->reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, TRUE);
    if (FAILED(hr)) goto done;
    hr = MFCreateMediaType(&request);
    if (FAILED(hr)) goto done;
    hr = IMFMediaType_SetGUID(request, &MF_MT_MAJOR_TYPE, &MFMediaType_Audio);
    if (FAILED(hr)) goto done;
    hr = IMFMediaType_SetGUID(request, &MF_MT_SUBTYPE, &MFAudioFormat_PCM);
    if (FAILED(hr)) goto done;
    hr = IMFMediaType_SetUINT32(request, &MF_MT_AUDIO_BITS_PER_SAMPLE, 16);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_SetCurrentMediaType(audio->reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, NULL, request);
    if (FAILED(hr)) goto done;
    hr = IMFSourceReader_GetCurrentMediaType(audio->reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, &format);
    if (FAILED(hr)) goto done;
    UINT32 bits = 0, channels = 0, frequency = 0;
    IMFMediaType_GetUINT32(format, &MF_MT_AUDIO_BITS_PER_SAMPLE, &bits);
    IMFMediaType_GetUINT32(format, &MF_MT_AUDIO_NUM_CHANNELS, &channels);
    IMFMediaType_GetUINT32(format, &MF_MT_AUDIO_SAMPLES_PER_SECOND, &frequency);
    if (bits != 16 || !channels || channels > 2 || !frequency || frequency > 192000) {
        hr = MF_E_INVALIDMEDIATYPE;
        goto done;
    }
    audio->block_align = channels * sizeof(int16_t);
    info[0] = (jint)frequency;
    info[1] = (jint)channels;
done:
    if (format) IMFMediaType_Release(format);
    if (request) IMFMediaType_Release(request);
    return hr;
}

JNIEXPORT jlong JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsAudio_open0(JNIEnv *env, jclass type, jstring file, jintArray metadata) {
    (void)type;
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) { fail(env, "Initialize audio COM", hr); return 0; }
    hr = MFStartup(MF_VERSION, MFSTARTUP_FULL);
    if (FAILED(hr)) { CoUninitialize(); fail(env, "Initialize audio decoder", hr); return 0; }
    Audio *audio = calloc(1, sizeof(*audio));
    jsize length = (*env)->GetStringLength(env, file);
    WCHAR *path = calloc((size_t)length + 1, sizeof(*path));
    jint info[2] = {0};
    if (audio && path) {
        (*env)->GetStringRegion(env, file, 0, length, (jchar *)path);
        hr = configure_audio(audio, path, info);
    } else {
        hr = E_OUTOFMEMORY;
    }
    free(path);
    if (SUCCEEDED(hr)) {
        (*env)->SetIntArrayRegion(env, metadata, 0, 2, info);
        return (jlong)(intptr_t)audio;
    }
    if (audio && audio->reader) IMFSourceReader_Release(audio->reader);
    free(audio);
    MFShutdown();
    CoUninitialize();
    fail(env, "Open background audio / PCM", hr);
    return 0;
}

JNIEXPORT jint JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsAudio_read0(JNIEnv *env, jclass type, jlong handle, jbyteArray target) {
    (void)type;
    Audio *audio = (Audio *)(intptr_t)handle;
    DWORD capacity = (DWORD)(*env)->GetArrayLength(env, target);
    HRESULT hr = S_OK;
    if (capacity < audio->block_align) {
        fail(env, "Audio destination too small", E_INVALIDARG);
        return 0;
    }
    for (int attempt = 0; attempt < 32; ++attempt) {
        if (!audio->buffer) {
            if (audio->eof) return -1;
            IMFSample *sample = NULL;
            DWORD flags = 0;
            hr = IMFSourceReader_ReadSample(audio->reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, 0, NULL, &flags, NULL, &sample);
            if (SUCCEEDED(hr) && (flags & MF_SOURCE_READERF_ERROR)) hr = E_FAIL;
            if (SUCCEEDED(hr) && sample) hr = IMFSample_ConvertToContiguousBuffer(sample, &audio->buffer);
            if (sample) IMFSample_Release(sample);
            if (FAILED(hr)) break;
            audio->eof = (flags & MF_SOURCE_READERF_ENDOFSTREAM) != 0;
            if (!audio->buffer) continue;
        }
        BYTE *data = NULL;
        DWORD length = 0;
        hr = IMFMediaBuffer_Lock(audio->buffer, &data, NULL, &length);
        if (FAILED(hr)) break;
        DWORD available = length - audio->offset;
        DWORD copied = capacity < available ? capacity : available;
        copied -= copied % audio->block_align;
        if (copied) (*env)->SetByteArrayRegion(env, target, 0, (jsize)copied, (const jbyte *)(data + audio->offset));
        audio->offset += copied;
        IMFMediaBuffer_Unlock(audio->buffer);
        if (length - audio->offset < audio->block_align) clear_audio_sample(audio);
        if (copied) return (jint)copied;
    }
    fail(env, "Decode background audio", FAILED(hr) ? hr : E_FAIL);
    return 0;
}

JNIEXPORT void JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsAudio_rewind0(JNIEnv *env, jclass type, jlong handle) {
    (void)type;
    Audio *audio = (Audio *)(intptr_t)handle;
    clear_audio_sample(audio);
    PROPVARIANT start = {0};
    start.vt = VT_I8;
    HRESULT hr = IMFSourceReader_SetCurrentPosition(audio->reader, &GUID_NULL, &start);
    if (FAILED(hr)) fail(env, "Seek to start of background audio", hr);
    else audio->eof = FALSE;
}

JNIEXPORT void JNICALL Java_dev_yachiyoc_ui_menuvideo_WindowsAudio_close0(JNIEnv *env, jclass type, jlong handle) {
    (void)env; (void)type;
    Audio *audio = (Audio *)(intptr_t)handle;
    clear_audio_sample(audio);
    IMFSourceReader_Release(audio->reader);
    free(audio);
    MFShutdown();
    CoUninitialize();
}
