FROM --platform=$TARGETOS/$TARGETARCH eclipse-temurin:21-jdk

RUN apt update
RUN apt -y install ffmpeg iproute2 git sqlite3 libsqlite3-dev python3 python3-dev ca-certificates dnsutils \
                   tzdata zip tar curl build-essential libtool iputils-ping libnss3 tini wget make libsqlite3-dev \
                   libonig-dev libxml2-dev libcurl4-openssl-dev libjpeg-dev libpng-dev libxpm-dev libmariadb-dev \
                   libpq-dev libicu-dev libfreetype6-dev libldap2-dev libxslt-dev libssl-dev libldb-dev

RUN useradd -m -d /home/container container

USER container
ENV USER=container HOME=/home/container
ENV DEBIAN_FRONTEND noninteractive
WORKDIR /home/container

COPY --chown=container:container ./../entrypoint.sh /entrypoint.sh

STOPSIGNAL SIGINT

RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/usr/bin/tini", "-g", "--"]
CMD ["/entrypoint.sh"]