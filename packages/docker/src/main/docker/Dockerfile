FROM onap/policy-jdk-debian

LABEL maintainer="Policy Team"

ARG BUILD_VERSION_DROOLS=${BUILD_VERSION_DROOLS}
ARG POLICY_LOGS=/var/log/onap/policy/pdpd
ARG POLICY_INSTALL=/tmp/policy-install
ARG MVN_SNAPSHOT_REPO_URL
ARG MVN_RELEASE_REPO_URL
ARG http_proxy

ENV BUILD_VERSION_DROOLS $BUILD_VERSION_DROOLS
ENV POLICY_INSTALL $POLICY_INSTALL
ENV POLICY_INSTALL_INIT $POLICY_INSTALL/config
ENV POLICY_LOGS $POLICY_LOGS
ENV POLICY_CONFIG $POLICY_HOME/config
ENV POLICY_LOGBACK $POLICY_CONFIG/logback.xml
ENV POLICY_DOCKER true
ENV MVN_SNAPSHOT_REPO_URL $MVN_SNAPSHOT_REPO_URL
ENV MVN_RELEASE_REPO_URL $MVN_RELEASE_REPO_URL
ENV http_proxy $http_proxy

RUN apt-get update \
    && apt-get install -y \
    httpie \
    python3 python3-pip \
    mariadb-client \
    file bash-completion \
    net-tools sudo \
    && update-alternatives --install /usr/bin/python python /usr/bin/python3 0 \
    && adduser policy sudo \
    && echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers \
    && pip install http-prompt

RUN mkdir -p $POLICY_CONFIG $POLICY_LOGS $POLICY_INSTALL_INIT && \
    chown -R policy:policy $POLICY_HOME $POLICY_LOGS $POLICY_INSTALL

COPY --chown=policy:policy /maven/install-drools.zip pdpd-entrypoint.sh $POLICY_INSTALL/

WORKDIR $POLICY_INSTALL
USER policy:policy

SHELL ["/bin/bash", "-c"]
RUN unzip -o install-drools.zip && \
    rm install-drools.zip && \
    chown -R policy:policy * && \
    mkdir -p $POLICY_HOME/logs $POLICY_HOME/config $HOME/.m2 && \
    tar -C $POLICY_HOME -xvf base-${BUILD_VERSION_DROOLS}.tar.gz --no-same-owner && \
    unzip policy-management-${BUILD_VERSION_DROOLS}.zip -d $POLICY_HOME && \
    echo "source $POLICY_HOME/etc/profile.d/env.sh" >> "$HOME/.bashrc" && \
    mv pdpd-entrypoint.sh $POLICY_HOME/bin/ && \
    chmod 700 $POLICY_HOME/bin/* && \
    chmod 600 $POLICY_HOME/config/* && \
    rm -f $POLICY_INSTALL/*.conf && \
    . $POLICY_HOME/etc/profile.d/env.sh && \
    $POLICY_HOME/bin/features install healthcheck distributed-locking lifecycle && \
    $POLICY_HOME/bin/features enable lifecycle && \
    find $HOME/.m2/ -name _maven.repositories -exec rm -v {} \; && \
    find $HOME/.m2/ -name _remote.repositories -exec rm -v {} \; && \
    rm $POLICY_INSTALL/policy-management-${BUILD_VERSION_DROOLS}.zip \
       $POLICY_INSTALL/base-${BUILD_VERSION_DROOLS}.tar.gz 2> /dev/null

EXPOSE 9696 6969
ENTRYPOINT ["/opt/app/policy/bin/pdpd-entrypoint.sh"]
CMD ["boot"]
