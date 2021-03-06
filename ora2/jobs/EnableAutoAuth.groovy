/*
    Variables consumed from the EXTRA_VARS input to your seed job in addition
    to those listed in the seed job.

    * FOLDER_NAME: some-folder
    * SSH_AGENT_KEY : your-ssh-credential-name
    * SSH_USER : your-ssh-username
    * NOTIFY_ON_FAILURE : e-mail-contact@example.com
*/

package ora2.jobs

import static org.edx.jenkins.dsl.DevopsConstants.common_wrappers
import static org.edx.jenkins.dsl.DevopsConstants.common_logrotator

class EnableAutoAuth {

    public static def job = { dslFactory, extraVars ->
        return dslFactory.job(extraVars.get('FOLDER_NAME', 'ora2') + '/enable-auto-auth') {
            wrappers common_wrappers
            logRotator common_logrotator
            properties {
                parameters {
                    stringParam(
                        'SANDBOX_HOST',
                        'ora2.sandbox.edx.org',
                        "The hostname of the sandbox."
                    )
                    stringParam(
                        "NOTIFY_ON_FAILURE",
                        extraVars.get("NOTIFY_ON_FAILURE", ''),
                        "Email to notify on failure"
                    )
                }
            }
            steps {
                shell(
                    "SSH_USER=${extraVars.get('SSH_USER', 'ubuntu')}\n" +
                    dslFactory.readFileFromWorkspace('ora2/resources/enable-auto-auth.sh')
                )
            }
            publishers {
                mailer('$NOTIFY_ON_FAILURE', true, false)
            }
            wrappers {
                sshAgent(extraVars['SSH_AGENT_KEY'])
            }
        }
    }

}
