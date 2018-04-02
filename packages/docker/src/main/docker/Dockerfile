FROM ubuntu:14.04

ARG HTTP_PROXY=${HTTP_PROXY}
ARG HTTPS_PROXY=${HTTPS_PROXY}
ARG POLICY_HOME=/opt/app/policy
ARG POLICY_LOGS=/var/log/onap/policy/pdpd
ARG POLICY_INSTALL=/tmp/policy-install

ENV http_proxy $HTTP_PROXY
ENV https_proxy $HTTPS_PROXY
ENV POLICY_INSTALL ${POLICY_INSTALL}
ENV POLICY_HOME ${POLICY_HOME}
ENV POLICY_LOGS ${POLICY_LOGS}
ENV POLICY_DOCKER true

RUN \
        apt-get clean && \
        apt-get update && \
        apt-get install -y zip unzip curl wget ssh telnet maven && \
        apt-get install -y software-properties-common && \
        apt-get install -y jq httpie && \
        apt-get install -y python-pip && \
        add-apt-repository ppa:openjdk-r/ppa && \
        apt-get clean && \
        apt-get update && \
        apt-get install -y openjdk-8-jdk

RUN useradd --create-home --shell /bin/bash policy

# install MariaDB client
RUN \
    apt-get install -y apt-transport-https && \
        apt-key adv --recv-keys --keyserver hkp://keyserver.ubuntu.com:80 0xcbcb082a1bb943db && \
        add-apt-repository 'deb [arch=amd64,i386,ppc64el] http://ftp.osuosl.org/pub/mariadb/repo/10.0/ubuntu trusty main' && \
        apt-get clean && \
        apt-get update && \
        apt-get install -y mariadb-client

RUN pip install http-prompt
RUN mkdir -p ${POLICY_HOME}/config ${POLICY_LOGS} ${POLICY_INSTALL}/config && \
    chown -R policy:policy ${POLICY_HOME} ${POLICY_LOGS} ${POLICY_INSTALL}

WORKDIR ${POLICY_INSTALL}

COPY install-drools.zip apps-controlloop.zip docker-install.sh do-start.sh wait-for-port.sh ./

VOLUME [ "${POLICY_INSTALL}/config", "${POLICY_HOME}/config" ]

RUN unzip -o install-drools.zip && \
    unzip -o apps-controlloop.zip && \
    rm install-drools.zip apps-controlloop.zip && \
    chown -R policy:policy * && \
    chmod +x *.sh

EXPOSE 9696 6969

USER policy

CMD ./do-start.sh
