FROM onap/policy/policy-base

RUN pip install http-prompt
RUN mkdir -p /opt/app/policy/opt /tmp/policy-install && \
    chown -R policy /opt/app/policy /tmp/policy-install

WORKDIR /tmp/policy-install

COPY install-drools.zip apps.zip docker-install.sh do-start.sh wait-for-port.sh ./

RUN unzip -o install-drools.zip && \
    unzip -o apps.zip && \
    rm install-drools.zip apps.zip && \
    chown -R policy * && \
    chmod +x *.sh

USER policy
CMD ./do-start.sh
