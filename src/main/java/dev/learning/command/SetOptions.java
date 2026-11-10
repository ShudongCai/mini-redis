package dev.learning.command;

public record SetOptions(
        Long ttlSeconds,
        boolean nx,
        boolean xx,
        boolean keepTtl
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long ttlSeconds;
        private boolean nx;
        private boolean xx;
        private boolean keepTtl;

        public Builder ttlSeconds(Long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder nx() {
            this.nx = true;
            return this;
        }

        public Builder xx() {
            this.xx = true;
            return this;
        }

        public Builder keepTtl() {
            this.keepTtl = true;
            return this;
        }

        public SetOptions build() {
            return new SetOptions(ttlSeconds, nx, xx, keepTtl);
        }


    }
}
