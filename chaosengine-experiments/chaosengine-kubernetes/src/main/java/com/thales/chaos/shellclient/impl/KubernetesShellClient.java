/*
 *    Copyright (c) 2019 Thales Group
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package com.thales.chaos.shellclient.impl;

import com.thales.chaos.constants.SSHConstants;
import com.thales.chaos.exception.ChaosException;
import com.thales.chaos.exception.enums.KubernetesChaosErrorCode;
import com.thales.chaos.shellclient.ShellClient;
import com.thales.chaos.shellclient.ShellOutput;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.v;

public class KubernetesShellClient implements ShellClient {
    private static final Logger log = LoggerFactory.getLogger(KubernetesShellClient.class);
    private Exec exec;
    private String namespace;
    private String podName;
    private String containerName;

    public static KubernetesShellClientBuilder builder () {
        return KubernetesShellClientBuilder.aKubernetesShellClient();
    }

    @Override
    public String runResource (Resource resource) {
        try {
            String innerCommand = String.format(SSHConstants.SCRIPT_NOHUP_WRAPPER, copyResourceToPath(resource));
            return runCommand(new String[]{ "sh", "-c", innerCommand }, false).getStdOut();
        } catch (IOException e) {
            throw new ChaosException(KubernetesChaosErrorCode.K8S_SHELL_CLIENT_ERROR, e);
        }
    }

    @Override
    public ShellOutput runCommand (String command) {
        return runCommand(command, true);
    }

    ShellOutput runCommand (String[] command, boolean getOutput) {
        log.debug("Running command {}, waiting for output = {}", v("command", command), getOutput);
        Process proc = null;
        try {
            proc = exec.exec(namespace, podName, command, containerName, false, false);
            int exitCode = proc.waitFor();
            if (getOutput) {
                ShellOutput shellOutput = ShellOutput.builder()
                                                     .withExitCode(exitCode)
                                                     .withStdOut(StreamUtils.copyToString(proc.getInputStream(), Charset
                                                             .defaultCharset()))
                                                     .build();
                if (exitCode > 0) {
                    log.debug("Command execution failed {}", v("failure", shellOutput));
                }
                return shellOutput;
            } else {
                return ShellOutput.EMPTY_SHELL_OUTPUT;
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while processing, throwing Interrupt up thread", e);
            Thread.currentThread().interrupt();
            return ShellOutput.EMPTY_SHELL_OUTPUT;
        } catch (IOException | ApiException e) {
            log.error("Received exception while processing command", e);
            throw new ChaosException(KubernetesChaosErrorCode.K8S_SHELL_CLIENT_ERROR, e);
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    ShellOutput runCommand (String command, boolean getOutput) {
        return runCommand(new String[]{ "sh", "-c", command }, getOutput);
    }

    String copyResourceToPath (Resource resource) throws IOException {
        return copyResourceToPath(resource, SSHConstants.TEMP_DIRECTORY);
    }

    String copyResourceToPath (Resource resource, String path) throws IOException {
        long contentLength = resource.contentLength();
        String finalPath = path + (path.endsWith("/") ? Strings.EMPTY : "/") + resource.getFilename();
        String[] command = new String[]{ "dd", "if=/dev/stdin", "of=" + finalPath, "bs=" + contentLength, "count=1" };
        log.debug("Transferring {} to {}/{} in path {}", resource, namespace, podName, path);
        Process proc = null;
        try {
            proc = exec.exec(namespace, podName, command, containerName, true, false);
            try (OutputStream os = proc.getOutputStream()) {
                try (InputStream is = resource.getInputStream()) {
                    StreamUtils.copy(is, os);
                }
            }
            if (!proc.waitFor(5000, TimeUnit.MILLISECONDS)) {
                throw new ChaosException(KubernetesChaosErrorCode.K8S_SHELL_TRANSFER_TIMEOUT);
            }
            int i = proc.exitValue();
            if (i == 0) {
                String command1 = "chmod 755 " + finalPath;
                runCommand(command1);
                return finalPath;
            }
            throw new ChaosException(KubernetesChaosErrorCode.K8S_SHELL_TRANSFER_FAIL);
        } catch (ApiException e) {
            log.error("Received exception while processing command", e);
            throw new ChaosException(KubernetesChaosErrorCode.K8S_SHELL_TRANSFER_FAIL, e);
        } catch (InterruptedException e) {
            log.error("Interrupted while processing, throwing Interrupt up thread", e);
            Thread.currentThread().interrupt();
            return Strings.EMPTY;
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    @Override
    public void close () {
        /*
        This does not maintain any persistent channels, so does not need to actually close, unlike most implementations
        of a shell client.
         */
    }

    public static final class KubernetesShellClientBuilder {
        private Exec exec;
        private String namespace;
        private String podName;
        private String containerName;

        private KubernetesShellClientBuilder () {
        }

        static KubernetesShellClientBuilder aKubernetesShellClient () {
            return new KubernetesShellClientBuilder();
        }

        public KubernetesShellClientBuilder withExec (Exec exec) {
            this.exec = exec;
            return this;
        }

        public KubernetesShellClientBuilder withNamespace (String namespace) {
            this.namespace = namespace;
            return this;
        }

        public KubernetesShellClientBuilder withPodName (String podName) {
            this.podName = podName;
            return this;
        }

        public KubernetesShellClientBuilder withContainerName (String containerName) {
            this.containerName = containerName;
            return this;
        }

        public KubernetesShellClient build () {
            KubernetesShellClient kubernetesShellClient = new KubernetesShellClient();
            kubernetesShellClient.exec = this.exec;
            kubernetesShellClient.podName = this.podName;
            kubernetesShellClient.namespace = this.namespace;
            kubernetesShellClient.containerName = this.containerName;
            return kubernetesShellClient;
        }
    }
}
