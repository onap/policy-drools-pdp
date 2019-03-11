FROM alpine:3.9

ARG BUILD_VERSION=${BUILD_VERSION}
ARG POLICY_HOME=/opt/app/policy
ARG POLICY_LOGS=/var/log/onap/policy/pdpd
ARG POLICY_INSTALL=/tmp/policy-install

ENV BUILD_VERSION ${BUILD_VERSION}
ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk

ENV POLICY_INSTALL ${POLICY_INSTALL}
ENV POLICY_INSTALL_INIT ${POLICY_INSTALL}/config
ENV POLICY_HOME ${POLICY_HOME}
ENV POLICY_LOGS ${POLICY_LOGS}
ENV POLICY_CONFIG ${POLICY_HOME}/config
ENV POLICY_LOGBACK ${POLICY_CONFIG}/logback.xml
ENV POLICY_DOCKER true

RUN apk --update add --no-cache busybox-extras \
                                bash coreutils grep findutils \
                                zip unzip curl \
                                wget openssh  maven jq \
                                httpie py-pip openjdk8

RUN addgroup -S policy && adduser -S policy -s /bin/bash

# install MariaDB client
RUN apk --update add --no-cache mariadb-client

RUN pip install http-prompt
RUN mkdir -p ${POLICY_HOME}/config ${POLICY_LOGS} ${POLICY_INSTALL_INIT} && \
    chown -R policy:policy ${POLICY_HOME} ${POLICY_LOGS} ${POLICY_INSTALL}

WORKDIR ${POLICY_INSTALL}

COPY install-drools.zip docker-install.sh do-start.sh wait-for-port.sh ./

VOLUME [ "${POLICY_INSTALL_INIT}" ]

RUN unzip -o install-drools.zip && \
    rm install-drools.zip && \
    chown -R policy:policy * && \
    chmod +x *.sh

EXPOSE 9696 6969

USER policy

CMD ./do-start.sh
