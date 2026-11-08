package dev.yachiyoc.ui.anim;

public enum Easing {
    LINEAR {
        @Override
        public float apply(float t) {
            return t;
        }
    },
    OUT_CUBIC {
        @Override
        public float apply(float t) {
            float inv = 1.0F - t;
            return 1.0F - inv * inv * inv;
        }
    },
    IN_OUT_CUBIC {
        @Override
        public float apply(float t) {
            return t < 0.5F
                    ? 4.0F * t * t * t
                    : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
        }
    },
    OUT_QUINT {
        @Override
        public float apply(float t) {
            return 1.0F - (float) Math.pow(1.0F - t, 5.0);
        }
    },
    /** 更快的收尾：起步就冲出去，适合"窗口/页面出现"。 */
    OUT_QUART {
        @Override
        public float apply(float t) {
            return 1.0F - (float) Math.pow(1.0F - t, 4.0);
        }
    },
    /** 极快收尾（Material 的 emphasized decelerate 近似），浮层弹出用。 */
    OUT_EXPO {
        @Override
        public float apply(float t) {
            return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0D, -10.0D * t);
        }
    },
    /** 略微过冲再回弹：弹窗出现时有点"扑"出来的手感。 */
    OUT_BACK {
        @Override
        public float apply(float t) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = t - 1.0F;
            return 1.0F + c3 * inv * inv * inv + c1 * inv * inv;
        }
    };

    public abstract float apply(float t);
}
