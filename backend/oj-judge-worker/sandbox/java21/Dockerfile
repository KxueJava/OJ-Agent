FROM eclipse-temurin:21-jdk

RUN sed -i 's#http://archive.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g; s#http://security.ubuntu.com/ubuntu#https://mirrors.aliyun.com/ubuntu#g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends gcc g++ \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 judge \
    && useradd --uid 10001 --gid judge --create-home --shell /usr/sbin/nologin judge

WORKDIR /workspace
