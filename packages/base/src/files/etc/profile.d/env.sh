#!/bin/sh
###
# ============LICENSE_START=======================================================
# ONAP
# ================================================================================
# Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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
# ============LICENSE_END=========================================================
###


# some non-docker environments may set up POLICY_HOME
# as a templated installation var (ie. ${{x}}) instead of
# an environment variable (case of docker passed to the
# container). The following condition accommodates that
# scenario.

templateRegex='^\$\{\{POLICY_HOME}}$'

if [ -z "${POLICY_HOME}" ]; then
    templatedPolicyHome='${{POLICY_HOME}}'
    if [[ ! ${templatedPolicyHome} =~ ${templateRegex} ]]; then
        POLICY_HOME=${templatedPolicyHome}
    fi
fi

set -a

POLICY_HOME=${POLICY_HOME:=/opt/app/policy}

if ls "${POLICY_HOME}"/etc/profile.d/*.conf > /dev/null 2>&1; then
    for conf in "${POLICY_HOME}"/etc/profile.d/*.conf ; do
        source ${conf}
    done
fi

for x in "${POLICY_HOME}"/bin "${JAVA_HOME}"/bin "${HOME}"/bin ; do
  if [ -d $x ] ; then
    case ":$PATH:" in
      *":$x:"*) :;; # already there
      *) PATH="$x:$PATH";;
    esac
  fi
done

set +a
