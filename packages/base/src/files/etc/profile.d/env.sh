###
# ============LICENSE_START=======================================================
# Base Package
# ================================================================================
# Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

export POLICY_HOME=${{POLICY_HOME}}
export POLICY_LOGS=${{POLICY_LOGS}}
export JAVA_HOME=${{JAVA_HOME}}
export ENGINE_MANAGEMENT_USER=${{ENGINE_MANAGEMENT_USER}}
export ENGINE_MANAGEMENT_PASSWORD=${{ENGINE_MANAGEMENT_PASSWORD}}
export ENGINE_MANAGEMENT_PORT=${{ENGINE_MANAGEMENT_PORT}}
export ENGINE_MANAGEMENT_HOST=${{ENGINE_MANAGEMENT_HOST}}

for x in $POLICY_HOME/bin $JAVA_HOME/bin $HOME/bin ; do
  if [ -d $x ] ; then
    case ":$PATH:" in
      *":$x:"*) :;; # already there
      *) PATH="$x:$PATH";;
    esac
  fi
done
