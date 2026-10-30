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
    };

    public abstract float apply(float t);
}
