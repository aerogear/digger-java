/**
 * Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aerogear.digger.client;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import com.offbytwo.jenkins.model.credentials.Credential;
import org.aerogear.digger.client.model.BuildDiscarder;
import org.aerogear.digger.client.model.BuildTriggerStatus;
import org.aerogear.digger.client.model.BuildParameter;
import org.aerogear.digger.client.model.LogStreamingOptions;
import org.aerogear.digger.client.services.ArtifactsService;
import org.aerogear.digger.client.services.BuildService;
import org.aerogear.digger.client.services.JobService;
import org.aerogear.digger.client.util.DiggerClientException;
import org.aerogear.digger.client.util.JenkinsAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Digger Java Client interact with Digger Jenkins api.
 */
public class DiggerClient {

    private static final Logger LOG = LoggerFactory.getLogger(DiggerClient.class);

    public static final long DEFAULT_BUILD_TIMEOUT = 60 * 1000;

    private JenkinsServer jenkinsServer;

    private JobService jobService;
    private BuildService buildService;
    private ArtifactsService artifactsService;

    private DiggerClient() {
    }

    /**
     * Create a client with defaults using provided url and credentials, with crumb flag turned off.
     * <p>
     * This client will use the defaults for the services. This is perfectly fine for majorith of the cases.
     *
     * @param url      Jenkins url
     * @param user     Jenkins user
     * @param password Jenkins password
     * @return client instance
     * @throws DiggerClientException if something goes wrong
     */
    public static DiggerClient createDefaultWithAuth(String url, String user, String password) throws DiggerClientException {
       return createDefaultWithAuth(url, user, password, false);
    }

    /**
     * Create a client with defaults using provided url and credentials, and specify if crumb is enabled on the Jenkins server.
     *
     * @param url      Jenkins url
     * @param user     Jenkins user
     * @param password Jenkins password
     * @param crumbFlag If CSRF Protection is enabled on the Jenkins server
     * @return client instance
     * @throws DiggerClientException if something goes wrong
     */
    public static DiggerClient createDefaultWithAuth(String url, String user, String password, boolean crumbFlag) throws DiggerClientException {
        BuildService buildService = new BuildService(BuildService.DEFAULT_FIRST_CHECK_DELAY, BuildService.DEFAULT_POLL_PERIOD);
        JobService jobService = new JobService(crumbFlag);
        ArtifactsService artifactsService = new ArtifactsService();
        return DiggerClient.builder()
            .createJobService(jobService)
            .triggerBuildService(buildService)
            .artifactsService(artifactsService)
            .withAuth(url, user, password)
            .build();
    }



    public static DiggerClientBuilder builder() {
        return new DiggerClientBuilder();
    }

    public static class DiggerClientBuilder {
        private JenkinsAuth auth;
        private JobService jobService;
        private BuildService buildService;
        private ArtifactsService artifactsService;

        public DiggerClientBuilder withAuth(String url, String user, String password) {
            this.auth = new JenkinsAuth(url, user, password);
            return this;
        }

        public DiggerClientBuilder createJobService(JobService jobService) {
            this.jobService = jobService;
            return this;
        }

        public DiggerClientBuilder triggerBuildService(BuildService buildService) {
            this.buildService = buildService;
            return this;
        }

        public DiggerClientBuilder artifactsService(ArtifactsService artifactsService) {
            this.artifactsService = artifactsService;
            return this;
        }

        public DiggerClient build() throws DiggerClientException {
            final DiggerClient client = new DiggerClient();
            try {
                client.jenkinsServer = new JenkinsServer(new URI(auth.getUrl()), auth.getUser(), auth.getPassword());
                client.jobService = this.jobService;
                client.buildService = this.buildService;
                client.artifactsService = this.artifactsService;
                return client;
            } catch (URISyntaxException e) {
                throw new DiggerClientException("Invalid jenkins url format.");
            }
        }
    }


    /**
     * Create new Digger job on Jenkins platform with build and binary cleanup
     *
     * @param name      job name that can be used later to reference job
     * @param gitRepo   git repository url (full git repository url. e.g git@github.com:wtrocki/helloworld-android-gradle.git
     * @param gitBranch git repository branch (default branch used to checkout source code)
     * @throws DiggerClientException if something goes wrong
     */
    public void createJob(String name, String gitRepo, String gitBranch, BuildDiscarder buildDiscarder) throws DiggerClientException {
        try {
            jobService.create(this.jenkinsServer, name, gitRepo, gitBranch, buildDiscarder);
        } catch (Throwable e) {
            throw new DiggerClientException(e);
        }
    }

    /**
     * Create new parameterized Digger job on Jenkins platform
     *
     * @param name            job name that can be used later to reference job
     * @param gitRepo         git repository url (full git repository url. e.g git@github.com:wtrocki/helloworld-android-gradle.git
     * @param gitBranch       git repository branch (default branch used to checkout source code)
     * @param gitRepoCredential the credential that will be used to clone the repo.
     * @param buildParameters list of parameters for a Jenkins parameterized build.
     * @throws DiggerClientException if something goes wrong
     */
    public void createJob(String name, String gitRepo, String gitBranch, BuildDiscarder buildDiscarder, Credential gitRepoCredential, List<BuildParameter> buildParameters) throws DiggerClientException {
        try {
            jobService.create(this.jenkinsServer, name, gitRepo, gitBranch, buildDiscarder, gitRepoCredential, buildParameters);
        } catch (Throwable e) {
            throw new DiggerClientException(e);
        }
    }

    /**
     * Update a Digger job on Jenkins platform
     *
     * @param name      job name that can be used later to reference job
     * @param gitRepo   git repository url (full git repository url. e.g git@github.com:wtrocki/helloworld-android-gradle.git
     * @param gitBranch git repository branch (default branch used to checkout source code)
     * @param buildDiscarder  BuildDiscarder instance. See {@link BuildDiscarder}
     * @throws DiggerClientException if something goes wrong
     */
    public void updateJob(String name, String gitRepo, String gitBranch, BuildDiscarder buildDiscarder) throws DiggerClientException {
        try {
            jobService.update(this.jenkinsServer, name, gitRepo, gitBranch, buildDiscarder);
        } catch (Throwable e) {
            throw new DiggerClientException(e);
        }
    }

    /**
     * Update parameterized Digger job on Jenkins platform.
     *
     * @param name            job name that can be used later to reference job
     * @param gitRepo         git repository url (full git repository url. e.g git@github.com:wtrocki/helloworld-android-gradle.git
     * @param gitBranch       git repository branch (default branch used to checkout source code)
     * @param buildDiscarder  BuildDiscarder instance. See {@link BuildDiscarder}
     * @param gitRepoCredential the credential that will be used to clone the repo
     * @param buildParameters list of parameters for a Jenkins parameterized build.
     * @throws DiggerClientException if something goes wrong
     */
    public void updateJob(String name, String gitRepo, String gitBranch, BuildDiscarder buildDiscarder, Credential gitRepoCredential, List<BuildParameter> buildParameters) throws DiggerClientException {
        try {
            jobService.update(this.jenkinsServer, name, gitRepo, gitBranch, buildDiscarder, gitRepoCredential, buildParameters);
        } catch (Throwable e) {
            throw new DiggerClientException(e);
        }
    }

    /**
     * Get a Digger job on the Jenkins platform. Null if not found.
     *
     * @param name name of the job to get
     * @throws DiggerClientException if something goes wrong
     */
     public JobWithDetails getJob(String name) throws DiggerClientException {
        try {
            return jobService.get(this.jenkinsServer, name);
        } catch (Throwable e) {
            throw new DiggerClientException(e);
        }
     }

    /**
     * Triggers a build for the given job and waits until it leaves the queue and actually starts.
     * <p>
     * Jenkins puts the build requests in a queue and once there is a slave available, it starts building
     * it and a build number is assigned to the build.
     * <p>
     * This method will block until there is a build number, or the given timeout period is passed. If the build is still in the queue
     * after the given timeout period, a {@code BuildStatus} is returned with state {@link BuildTriggerStatus.State#TIMED_OUT}.
     * <p>
     * Please note that timeout period is never meant to be very precise. It has the resolution of {@link BuildService#DEFAULT_POLL_PERIOD} because
     * timeout is checked before every pull.
     * <p>
     * Similarly, {@link BuildTriggerStatus.State#CANCELLED_IN_QUEUE} is returned if the build is cancelled on Jenkins side and
     * {@link BuildTriggerStatus.State#STUCK_IN_QUEUE} is returned if the build is stuck.
     *
     * @param jobName name of the job to trigger the build
     * @param timeout how many milliseconds should this call block before returning {@link BuildTriggerStatus.State#TIMED_OUT}.
     *                Should be larger than {@link BuildService#DEFAULT_FIRST_CHECK_DELAY}
     * @param params build parameters to be sent to the Jenkins build
     * @return the build status
     * @throws DiggerClientException if connection problems occur during connecting to Jenkins
     */
    public BuildTriggerStatus build(String jobName, long timeout, Map<String, String> params) throws DiggerClientException {
        try {
            BuildTriggerStatus buildTriggerStatus = buildService.triggerBuild(this.jenkinsServer, jobName, params);
            return buildService.pollBuild(this.jenkinsServer, jobName, buildTriggerStatus.getQueueReference(),timeout, params);
        } catch (IOException e) {
            LOG.debug("Exception while connecting to Jenkins", e);
            throw new DiggerClientException(e);
        } catch (InterruptedException e) {
            LOG.debug("Exception while waiting on Jenkins", e);
            throw new DiggerClientException(e);
        } catch (Throwable e) {
            LOG.debug("Exception while triggering a build", e);
            throw new DiggerClientException(e);
        }
    }

    /**
     * Triggers a build for the given job and waits until it leaves the queue and actually starts.
     * <p>
     * Calls {@link #build(String, long, Map)} with a default timeout of {@link #DEFAULT_BUILD_TIMEOUT} and no build parameters.
     *
     * @param jobName name of the job
     * @param timeout how many milliseconds should this call block before returning {@link BuildTriggerStatus.State#TIMED_OUT}.
     *                Should be larger than {@link BuildService#DEFAULT_FIRST_CHECK_DELAY}
     * @return the build status
     * @throws DiggerClientException if connection problems occur during connecting to Jenkins
     * @see #build(String, long)
     */
    public BuildTriggerStatus build(String jobName, long timeout) throws DiggerClientException {
        return this.build(jobName, timeout, Collections.<String, String>emptyMap());
    }

    /**
     * Triggers a build for the given job. Unlike {@link #build(String, long, Map)}, this method returns immediately with the build status, which also has a queue reference in it to track the build
     * @param jobName name of the job
     * @param params build parameters to be sent to the Jenkins build
     * @return The QueueReference
     * @throws DiggerClientException if connection problems occur during connecting to Jenkins
     */
    public BuildTriggerStatus triggerBuild(String jobName, Map<String, String> params) throws DiggerClientException {
        try{
            return buildService.triggerBuild(this.jenkinsServer, jobName, params);
        } catch (IOException e){
            LOG.debug("Exception while connecting to Jenkins", e);
            throw new DiggerClientException("Exception while connecting to Jenkins", e);
        } catch (InterruptedException e){
            LOG.debug("Exception while waiting on Jenkins", e);
            throw new DiggerClientException("Exception while waiting on Jenkins", e);
        } catch (Throwable e){
            LOG.debug("Exception while triggering a build", e);
            throw new DiggerClientException("Exception while triggering a build", e);
        }
    }

    /**
     * This method takes a QueueReference and polls to check if a build goes to the next available executor or gets cancelled or gets stuck on the queue for some other reason.
     * @param queueReference The queue reference
     * @param jobName name of the job
     * @param timeout
     * @param params build parameters to be sent to the Jenkins build
     * @return The build status
     * @throws DiggerClientException if connection problems occur during connecting to Jenkins
     */
    public BuildTriggerStatus pollBuild(String jobName, QueueReference queueReference, long timeout, Map<String, String> params) throws DiggerClientException {
        try{
            return buildService.pollBuild(this.jenkinsServer, jobName, queueReference, timeout, params);
        } catch (IOException e) {
            LOG.debug("Exception while connecting to Jenkins", e);
            throw new DiggerClientException("Exception while connecting to Jenkins", e);
        } catch (InterruptedException e) {
            LOG.debug("Exception while waiting on Jenkins", e);
            throw new DiggerClientException("Exception while waiting on Jenkins", e);
        }  catch (Throwable e){
            LOG.debug("Exception while polling a build", e);
            throw new DiggerClientException("Exception while polling a build", e);
        }
    }

    /**
     * Triggers a build for the given job and waits until it leaves the queue and actually starts.
     * <p>
     * Calls {@link #build(String, long, Map)} with a default timeout of {@link #DEFAULT_BUILD_TIMEOUT} and no build parameters.
     *
     * @param jobName name of the job
     * @return the build status
     * @throws DiggerClientException if connection problems occur during connecting to Jenkins
     * @see #build(String, long)
     */
    public BuildTriggerStatus build(String jobName) throws DiggerClientException {
        return this.build(jobName, DEFAULT_BUILD_TIMEOUT, Collections.<String, String>emptyMap());
    }

    /**
     * Fetch artifacts urls for specific job and build number
     *
     * @param jobName      name of the job
     * @param buildNumber  job build number
     * @param artifactName - name of the artifact to fetch - can be regexp
     * @return InputStream with file contents
     * @throws DiggerClientException - when problem with fetching artifacts from jenkins
     */
    public InputStream fetchArtifact(String jobName, int buildNumber, String artifactName) throws DiggerClientException {
        return artifactsService.streamArtifact(jenkinsServer, jobName, buildNumber, artifactName);
    }

    /**
     * Save artifact for specified location for specific job, build number and artifact name.
     * If name would be an regular expression method would return stream for the first match.
     *
     * @param jobName      name of the job
     * @param buildNumber  job build number
     * @param artifactName name of the artifact to fetch - can be regexp for example *.apk
     * @param outputFile   file (location) used to save artifact
     * @throws DiggerClientException when problem with fetching artifacts from jenkins
     */
    public void saveArtifact(String jobName, int buildNumber, String artifactName, File outputFile) throws DiggerClientException {
        try{
            artifactsService.saveArtifact(jenkinsServer, jobName, buildNumber, artifactName, outputFile);
        } catch (IOException e) {
            LOG.debug("Exception while saving a file", e);
            throw new DiggerClientException("Exception while saving a file", e);
        }
    }

    /**
     * Get build logs for specific job and build number
     *
     * @param jobName     name of the job
     * @param buildNumber job build number
     * @return String with file contents that can be saved or piped to socket
     * @throws DiggerClientException when problem with fetching artifacts from jenkins
     */
    public String getBuildLogs(String jobName, int buildNumber) throws DiggerClientException {
        try {
            return buildService.getBuildLogs(jenkinsServer, jobName, buildNumber);
        } catch (IOException e) {
            LOG.debug("Exception while retrieving logs", e);
            throw new DiggerClientException("Exception while retrieving logs", e);
        }
    }

    /**
     * Returns the build history for a job. As reported by {@link JobWithDetails#getBuilds()} it will return max 100 most-recent builds.
     * <p>
     * Please note that this approach will take some time since we first fetch the builds in 1 call, then fetch build details in 1 call per build.
     *
     * @param jobName name of the job
     * @return the build history
     * @throws DiggerClientException if connection problems occur
     */
    public List<BuildWithDetails> getBuildHistory(String jobName) throws DiggerClientException {
        return buildService.getBuildHistory(jenkinsServer, jobName);
    }

    /**
     * Streaming the build logs.
     *
     * @param jobName the job name
     * @param buildNumber the build number
     * @param options See {@link LogStreamingOptions}
     * @throws DiggerClientException
     */
    public void streamLogs(String jobName, int buildNumber, LogStreamingOptions options) throws DiggerClientException {
        try{
            buildService.streamBuildLogs(jenkinsServer, jobName, buildNumber, options);
        } catch (InterruptedException e) {
            LOG.debug("Exception while waiting", e);
            throw new DiggerClientException("Exception while waiting", e);
        } catch (IOException e) {
            LOG.debug("Exception while streaming logs", e);
            throw new DiggerClientException("Exception while streaming logs", e);
        }
    }

    /**
     * Delete the job from Jenkins server.
     *
     * @param jobName the name of the job to delete.
     * @param credentialId the id of the credential to delete. It should be the same id value if gitRepoCredential is provided in #createJob(String, String, String, Credential, List) and it has an id value. Otherwise pass null.
     * @throws DiggerClientException
     */
    public void deleteJob(String jobName, String credentialId) throws DiggerClientException {
        try {
            jobService.delete(jenkinsServer, jobName, credentialId);
        } catch(IOException ioe) {
            throw new DiggerClientException(ioe);
        }
    }

    /**
     * Get the details about a build.
     *
     * @param jobName the name of the job
     * @param buildNumber the build number
     * @return the build details
     * @throws DiggerClientException
     */
    public BuildWithDetails getBuildDetails(String jobName, int buildNumber) throws DiggerClientException {
        try {
            return buildService.getBuildDetails(jenkinsServer, jobName, buildNumber);
        } catch (IOException e) {
            LOG.debug("Exception while connecting to Jenkins", e);
            throw new DiggerClientException("Exception while connecting to Jenkins", e);
        }
    }

    /**
     * Cancel a build for a specific job
     *
     * @param jobName the name of the job
     * @param buildNumber the build number
     * @throws DiggerClientException
     */
    public BuildWithDetails cancelBuild(String jobName, int buildNumber) throws DiggerClientException {
        try {
            return buildService.cancelBuild(jenkinsServer, jobName, buildNumber);
        } catch (IOException e) {
            LOG.debug("Exception while connecting to Jenkins", e);
            throw new DiggerClientException("Exception while connecting to Jenkinss", e);
        }
    }

    /**
     * Expose the underline Jenkins Server client to allow perform other operations that may not be implemented by the jenkins digger client.
     * @return the instance of the jenkins server
     */
    public JenkinsServer getJenkinsServer() {
        return this.jenkinsServer;
    }
}
