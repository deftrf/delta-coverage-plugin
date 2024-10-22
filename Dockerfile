FROM rust:1.75 AS base
RUN cargo install --locked cargo-chef sccache
ENV RUSTC_WRAPPER=sccache SCCACHE_DIR=/sccache
 
FROM base AS planner
WORKDIR /app
COPY . .
RUN cargo chef prepare --recipe-path recipe.json
 
FROM base as builder
WORKDIR /app
USER wat
COPY --from=planner /app/recipe.json recipe.json
RUN --mount=type=cache,target=/usr/local/cargo/registry \
--mount=type=cache,target=/usr/local/cargo/git \
--mount=type=cache,target=$SCCACHE_DIR,sharing=locked \
cargo chef cook --release --recipe-path recipe.json
COPY . .
RUN --mount=type=cache,target=/usr/local/cargo/registry \
--mount=type=cache,target=/usr/local/cargo/git \
--mount=type=cache,target=$SCCACHE_DIR,sharing=locked \
cargo build