#!/usr/bin/env ash

# ########################################################################
# Copyright 2019-2020 AT&T Intellectual Property. All rights reserved
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ########################################################################


function maven {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- maven --"
        set -x
    fi

    if [ -f "${POLICY_INSTALL_INIT}"/settings.xml ]; then
        if ! cmp -s "${POLICY_INSTALL_INIT}"/settings.xml "${POLICY_HOME}"/etc/m2/settings.xml; then
            echo "overriding settings.xml"
            cp -f "${POLICY_INSTALL_INIT}"/settings.xml "${POLICY_HOME}"/etc/m2
        fi
    fi

    if [ -f "${POLICY_INSTALL_INIT}"/standalone-settings.xml ]; then
        if ! cmp -s "${POLICY_INSTALL_INIT}"/standalone-settings.xml "${POLICY_HOME}"/etc/m2/standalone-settings.xml; then
            echo "overriding standalone-settings.xml"
            cp -f "${POLICY_INSTALL_INIT}"/standalone-settings.xml "${POLICY_HOME}"/etc/m2
        fi
    fi
}

function systemConfs {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- systemConfs --"
        set -x
    fi

    local confName

    if ! ls "${POLICY_INSTALL_INIT}"/*.conf > /dev/null 2>&1; then
        return 0
    fi

    for c in $(ls "${POLICY_INSTALL_INIT}"/*.conf 2> /dev/null); do
        echo "adding system conf file: ${c}"
        cp -f "${c}" "${POLICY_HOME}"/etc/profile.d/
        confName="$(basename "${c}")"
        sed -i -e "s/ *= */=/" -e "s/=\([^\"\']*$\)/='\1'/" "${POLICY_HOME}/etc/profile.d/${confName}"
    done

    source "${POLICY_HOME}"/etc/profile.d/env.sh
}

function features {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- features --"
        set -x
    fi

    if ! ls "${POLICY_INSTALL_INIT}"/features*.zip > /dev/null 2>&1; then
        return 0
    fi

    source "${POLICY_HOME}"/etc/profile.d/env.sh

    for f in $(ls "${POLICY_INSTALL_INIT}"/features*.zip 2> /dev/null); do
        echo "installing feature: ${f}"
        "${POLICY_HOME}"/bin/features install "${f}"
    done
}

function scripts {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- scripts --"
        set -x
    fi

    local scriptExtSuffix=${1:-"sh"}

    if ! ls "${POLICY_INSTALL_INIT}"/*."${scriptExtSuffix}" > /dev/null 2>&1; then
        return 0
    fi

    source "${POLICY_HOME}"/etc/profile.d/env.sh

    for s in $(ls "${POLICY_INSTALL_INIT}"/*."${scriptExtSuffix}" 2> /dev/null); do
        echo "executing script: ${s}"
        source "${s}"
    done
}

function security {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- security --"
        set -x
    fi

    if [ -f "${POLICY_INSTALL_INIT}"/policy-keystore ]; then
        if ! cmp -s "${POLICY_INSTALL_INIT}"/policy-keystore "${POLICY_HOME}"/etc/ssl/policy-keystore; then
            echo "overriding policy-keystore"
            cp -f "${POLICY_INSTALL_INIT}"/policy-keystore "${POLICY_HOME}"/etc/ssl
        fi
    fi

    if [ -f "${POLICY_INSTALL_INIT}"/policy-truststore ]; then
        if ! cmp -s "${POLICY_INSTALL_INIT}"/policy-truststore "${POLICY_HOME}"/etc/ssl/policy-truststore; then
            echo "overriding policy-truststore"
            cp -f "${POLICY_INSTALL_INIT}"/policy-truststore "${POLICY_HOME}"/etc/ssl
        fi
    fi

    if [ -f "${POLICY_INSTALL_INIT}"/aaf-cadi.keyfile ]; then
        if ! cmp -s "${POLICY_INSTALL_INIT}"/aaf-cadi.keyfile "${POLICY_HOME}"/config/aaf-cadi.keyfile; then
            echo "overriding aaf-cadi.keyfile"
            cp -f "${POLICY_INSTALL_INIT}"/aaf-cadi.keyfile "${POLICY_HOME}"/config/aaf-cadi.keyfile
        fi
    fi
}

function serverConfig {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- serverConfig --"
        set -x
    fi

    local configExtSuffix=${1:-"properties"}

    if ! ls "${POLICY_INSTALL_INIT}"/*."${configExtSuffix}" > /dev/null 2>&1; then
        return 0
    fi

    for p in $(ls "${POLICY_INSTALL_INIT}"/*."${configExtSuffix}" 2> /dev/null); do
        echo "configuration ${configExtSuffix}: ${p}"
        cp -f "${p}" "${POLICY_HOME}"/config
    done
}

function db {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- db --"
        set -x
    fi

    if [ -z "${SQL_HOST}" ]; then
        return 0
    fi

    echo "Wating for ${SQL_HOST} ."
    timeout 120 ash -c 'until nc -vz "${SQL_HOST}" 3306; do echo -n "."; sleep 1; done'; echo $?

    "${POLICY_HOME}"/bin/db-migrator -s ALL -o upgrade
}

function inspect {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- inspect --"
        set -x
    fi

    echo "ENV: "
    env
    echo
    echo

    source "${POLICY_HOME}"/etc/profile.d/env.sh
    policy status

    echo
    echo
}

function reload {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- reload --"
        set -x
    fi

    systemConfs
    maven
    features
    security
    serverConfig "properties"
    serverConfig "xml"
    serverConfig "json"
    scripts "pre.sh"
}

function start {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- start --"
        set -x
    fi

    source "${POLICY_HOME}"/etc/profile.d/env.sh
    policy start
}

function configure {
    if [ "${DEBUG}" = "y" ]]; then
        echo "-- configure --"
        set -x
    fi

    reload
    db
}

function vmBoot {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- vmBoot --"
        set -x
    fi

    reload
    db
    start
    scripts "post.sh"
}

function dockerBoot {
    if [ "${DEBUG}" = "y" ]; then
        echo "-- dockerBoot --"
        set -x
    fi

    set -e

    configure

    source "${POLICY_HOME}"/etc/profile.d/env.sh
    policy exec
}

if [ "${DEBUG}" = "y" ]; then
    echo "-- $0 $* --"
    set -x
fi

operation="${1}"
case "${operation}" in
    inspect)    inspect
                ;;
    boot)       dockerBoot
                ;;
    vmboot)     vmBoot
                ;;
    configure)  configure
                ;;
    *)          exec "$@"
                ;;
esac
