package devops

import org.yaml.snakeyaml.Yaml
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_LOG_ROTATOR
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_GITHUB_BASEURL
import static org.edx.jenkins.dsl.JenkinsPublicConstants.JENKINS_PUBLIC_JUNIT_REPORTS
import static org.edx.jenkins.dsl.JenkinsPublicConstants.GHPRB_WHITELIST_BRANCH

/*
Example secret YAML file used by this script
publicJobConfig:
    open : true/false
    jobName : name-of-jenkins-job-to-be
    subsetJob : name-of-test-subset-job
    repoName : name-of-github-edx-repo
    coverageJob : name-of-unit-coverage-job
    testengUrl: testeng-github-url-segment
    platformUrl : platform-github-url-segment
    testengCredential : n/a
    platformCredential : n/a
    platformCloneReference : clone/.git
    workerLabel: worker-label
    whitelistBranchRegex: 'release/*'
    context: jenkins/test
    triggerPhrase: 'jenkins run test'
    defaultTestengBranch: 'master'
*/

/* stdout logger */
Map config = [:]
Binding bindings = getBinding()
config.putAll(bindings.getVariables())
PrintStream out = config['out']

/* Map to hold the k:v pairs parsed from the secret file */
Map secretMap = [:]
Map ghprbMap = [:]
try {
    out.println('Parsing secret YAML file')
    String secretFileContents = new File("${EDX_PLATFORM_TEST_PYTHON_PR_SECRET}").text
    String ghprbConfigContents = new File("${GHPRB_SECRET}").text
    Yaml yaml = new Yaml()
    secretMap = yaml.load(secretFileContents)
    ghprbMap = yaml.load(ghprbConfigContents)
    out.println('Successfully parsed secret YAML file')
}
catch (any) {
    out.println('Jenkins DSL: Error parsing secret YAML file')
    out.println('Exiting with error code 1')
    return 1
}
/* Iterate over the job configurations */
secretMap.each { jobConfigs ->

    Map jobConfig = jobConfigs.getValue()

    /* Test secret contains all necessary keys for this job */
    assert jobConfig.containsKey('open')
    assert jobConfig.containsKey('jobName')
    assert jobConfig.containsKey('subsetJob')
    assert jobConfig.containsKey('repoName')
    assert jobConfig.containsKey('coverageJob')
    assert jobConfig.containsKey('testengUrl')
    assert jobConfig.containsKey('platformUrl')
    assert jobConfig.containsKey('testengCredential')
    assert jobConfig.containsKey('platformCredential')
    assert jobConfig.containsKey('platformCloneReference')
    assert jobConfig.containsKey('workerLabel')
    assert jobConfig.containsKey('whitelistBranchRegex')
    assert jobConfig.containsKey('context')
    assert jobConfig.containsKey('triggerPhrase')
    assert jobConfig.containsKey('defaultTestengBranch')
    assert ghprbMap.containsKey('admin')
    assert ghprbMap.containsKey('userWhiteList')
    assert ghprbMap.containsKey('orgWhiteList')

    buildFlowJob(jobConfig['jobName']) {
        if (!jobConfig['open'].toBoolean()) {
            authorization {
                blocksInheritance(true)
                permissionAll('edx')
                permission('hudson.model.Item.Discover', 'anonymous')
            }
        }
        properties {
              githubProjectUrl(JENKINS_PUBLIC_GITHUB_BASEURL + jobConfig['platformUrl'])
        }
        logRotator JENKINS_PUBLIC_LOG_ROTATOR()
        concurrentBuild()
        label('flow-worker-python')
        checkoutRetryCount(5)
        environmentVariables {
            env('SUBSET_JOB', jobConfig['subsetJob'])
            env('REPO_NAME', jobConfig['repoName'])
            env('COVERAGE_JOB', jobConfig['coverageJob'])
        }
        parameters {
            stringParam('WORKER_LABEL', jobConfig['workerLabel'], 'Jenkins worker for running the test subset jobs')
        }
        multiscm {
            git {
                remote {
                    url(JENKINS_PUBLIC_GITHUB_BASEURL + jobConfig['testengUrl'] + '.git')
                    if (!jobConfig['open'].toBoolean()) {
                        credentials(jobConfig['testengCredential'])
                    }
                }
                branch(jobConfig['defaultTestengBranch'])
                browser()
                extensions {
                    cleanBeforeCheckout()
                    relativeTargetDirectory('testeng-ci')
                }
            }
           git {
                remote {
                    url(JENKINS_PUBLIC_GITHUB_BASEURL + jobConfig['platformUrl'] + '.git')
                    refspec('+refs/pull/*:refs/remotes/origin/pr/*')
                    if (!jobConfig['open'].toBoolean()) {
                        credentials(jobConfig['platformCredential'])
                    }
                }
                branch('\${ghprbActualCommit}')
                browser()
                extensions {
                    relativeTargetDirectory(jobConfig['repoName'])
                    cloneOptions {
                        reference("\$HOME/" + jobConfig['platformCloneReference'])
                        timeout(10)
                    }
                    cleanBeforeCheckout()
                }
            }
        }
        triggers {
            pullRequest {
                admins(ghprbMap['admin'])
                useGitHubHooks()
                triggerPhrase(jobConfig['triggerPhrase'])
                userWhitelist(ghprbMap['userWhiteList'])
                orgWhitelist(ghprbMap['orgWhiteList'])
                extensions {
                    commitStatus {
                        context(jobConfig['context'])
                    }
                }
            }
        }

        configure GHPRB_WHITELIST_BRANCH(jobConfig['whitelistBranchRegex'])

        dslFile('testeng-ci/jenkins/flow/pr/edx-platform-python-unittests-pr.groovy')
        publishers {
           archiveJunit(JENKINS_PUBLIC_JUNIT_REPORTS) {
               retainLongStdout()
           }
           publishHtml {
               report("${jobConfig['repoName']}/reports") {
                   reportFiles('diff_coverage_combined.html')
                   reportName('Diff Coverage Report')
                   keepAll()
               }
           }
           configure { node ->
               node / publishers << 'jenkins.plugins.shiningpanda.publishers.CoveragePublisher' {
               }
           }
       }
    }
}
