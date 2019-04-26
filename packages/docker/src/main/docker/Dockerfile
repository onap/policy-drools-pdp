FROM onap/policy-common-alpine:1.4.0

LABEL maintainer="Policy Team"

ARG BUILD_VERSION=${BUILD_VERSION}
ARG POLICY_LOGS=/var/log/onap/policy/pdpd
ARG POLICY_INSTALL=/tmp/policy-install

ENV BUILD_VERSION ${BUILD_VERSION}
ENV POLICY_INSTALL ${POLICY_INSTALL}
ENV POLICY_INSTALL_INIT ${POLICY_INSTALL}/config
ENV POLICY_LOGS ${POLICY_LOGS}
ENV POLICY_CONFIG ${POLICY_HOME}/config
ENV POLICY_LOGBACK ${POLICY_CONFIG}/logback.xml
ENV POLICY_DOCKER true

RUN apk add --no-cache \
    mariadb-client \
    file \
    bash-completion \
    openssl \
    less \
    && pip install http-prompt

RUN mkdir -p ${POLICY_CONFIG} ${POLICY_LOGS} ${POLICY_INSTALL_INIT} && \
    chown -R policy:policy ${POLICY_LOGS} ${POLICY_INSTALL} && \
    rmdir ${POLICY_HOME}/etc/ssl && \
    rmdir ${POLICY_HOME}/etc

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
