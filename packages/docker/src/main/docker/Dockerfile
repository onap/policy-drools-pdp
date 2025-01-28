#-------------------------------------------------------------------------------
# Dockerfile
# ============LICENSE_START=======================================================
#  Copyright (C) 2020-2021 AT&T Intellectual Property. All rights reserved.
#  Modifications Copyright (C) 2022-2024 Nordix Foundation.
# ================================================================================
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# ============LICENSE_END=========================================================
#-------------------------------------------------------------------------------
FROM onap/policy-jdk-alpine:4.1.0

LABEL maintainer="Policy Team"
LABEL org.opencontainers.image.title="Policy Drools PDP"
LABEL org.opencontainers.image.description="Policy Drools PDP image based on Alpine"
LABEL org.opencontainers.image.url="https://github.com/onap/policy-drools-pdp"
LABEL org.opencontainers.image.vendor="ONAP Policy Team"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.created="${git.build.time}"
LABEL org.opencontainers.image.version="${git.build.version}"
LABEL org.opencontainers.image.revision="${git.commit.id.abbrev}"

ARG BUILD_VERSION_DROOLS=${BUILD_VERSION_DROOLS}
ARG POLICY_LOGS=/var/log/onap/policy/pdpd
ARG POLICY_INSTALL=/tmp/policy-install
ARG MVN_SNAPSHOT_REPO_URL
ARG MVN_RELEASE_REPO_URL
ARG http_proxy

ENV BUILD_VERSION_DROOLS $BUILD_VERSION_DROOLS
ENV JAVA_HOME /usr/lib/jvm/java-17-openjdk
ENV POLICY_INSTALL $POLICY_INSTALL
ENV POLICY_INSTALL_INIT $POLICY_INSTALL/config
ENV POLICY_LOGS $POLICY_LOGS
ENV POLICY_CONFIG $POLICY_HOME/config
ENV POLICY_LOGBACK $POLICY_CONFIG/logback.xml
ENV POLICY_DOCKER true
ENV MVN_SNAPSHOT_REPO_URL $MVN_SNAPSHOT_REPO_URL
ENV MVN_RELEASE_REPO_URL $MVN_RELEASE_REPO_URL
ENV http_proxy $http_proxy

USER root
RUN apk update && \
    apk add --no-cache file maven \
    net-tools netcat-openbsd sudo less vim openssl \
    && python3 -m pip install --no-cache-dir --upgrade setuptools http-prompt \
    && python3 -m pip install --no-cache-dir httpie

RUN mkdir -p $POLICY_CONFIG $POLICY_LOGS $POLICY_INSTALL_INIT && \
    chown -R policy:policy $POLICY_HOME $POLICY_LOGS $POLICY_INSTALL

COPY --chown=policy:policy /maven/install-drools.zip pdpd-entrypoint.sh $POLICY_INSTALL/

WORKDIR $POLICY_INSTALL
USER policy:policy

SHELL ["/bin/sh", "-c"]
RUN unzip -o install-drools.zip && \
    rm install-drools.zip && \
    chown -R policy:policy * && \
    mkdir -p $POLICY_HOME/logs $POLICY_HOME/config $HOME/.m2 && \
    tar -C $POLICY_HOME -xvf base-${BUILD_VERSION_DROOLS}.tar.gz --no-same-owner && \
    unzip policy-management-${BUILD_VERSION_DROOLS}.zip -d $POLICY_HOME && \
    echo "source $POLICY_HOME/etc/profile.d/env.sh" >> "$HOME/.profile" && \
    mv pdpd-entrypoint.sh $POLICY_HOME/bin/ && \
    chmod 700 $POLICY_HOME/bin/* && \
    chmod 600 $POLICY_HOME/config/* && \
    rm -f $POLICY_INSTALL/*.conf && \
    . $POLICY_HOME/etc/profile.d/env.sh && \
    $POLICY_HOME/bin/features install healthcheck distributed-locking lifecycle no-locking legacy-config && \
    $POLICY_HOME/bin/features enable lifecycle && \
    find $HOME/.m2/ -name _maven.repositories -exec rm -v {} \; && \
    find $HOME/.m2/ -name _remote.repositories -exec rm -v {} \; && \
    rm $POLICY_INSTALL/policy-management-${BUILD_VERSION_DROOLS}.zip \
       $POLICY_INSTALL/base-${BUILD_VERSION_DROOLS}.tar.gz 2> /dev/null

EXPOSE 9696 6969
ENTRYPOINT ["/opt/app/policy/bin/pdpd-entrypoint.sh"]
CMD ["boot"]
