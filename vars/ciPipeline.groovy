// This is the universal script for both Bitbucket and GitHub (Final Version)
def call(Map config = [:]) {
    pipeline {
        agent { label 'debian-master' }

        // This IS still needed to stop the broken default checkout
        options {
            skipDefaultCheckout true
        }

        environment {
            SONAR_AUTH_TOKEN = credentials('sonar-global-token')
            // Credential ID for Bitbucket Git checkout
            BITBUCKET_GIT_CREDS_ID = 'devops.thirdeyecreative' 
        }

        stages {
            
            stage('Checkout Code') {
                steps {
                    script {
                        // This is the new, more robust check.
                        // It checks the class of the SCM object itself.
                        if (scm.getClass().getName().contains('BitbucketSCMSource')) {
                            
                            // --- IF IT'S A BITBUCKET BUILD ---
                            echo "Bitbucket SCM detected. Overriding checkout credentials."
                            
                            // 1. Get the SCM definition from the job
                            def bitbucketScm = scm
                            
                            // 2. FORCE the credentialId to be our Git credential
                            // This is the most important part.
                            bitbucketScm.userRemoteConfigs[0].credentialsId = BITBUCKET_GIT_CREDS_ID
                            
                            // 3. Now, call checkout with the *modified* scm object
                            checkout bitbucketScm
                            
                        } else {
                            // --- IF IT'S A GITHUB BUILD ---
                            echo "GitHub SCM detected. Running standard checkout scm."
                            // This will run for your GitHub builds and will work correctly.
                            checkout scm
                        }
                    }
                }
            }

            stage('SonarQube Analysis') {
                steps {
                    script {
                        withSonarQubeEnv('MySonarQube') {
                            if (env.CHANGE_ID) {
                                echo "INFO: Pull Request build detected. Running SonarQube PR analysis."
                                
                                // This logic correctly finds the PR branch name for BOTH platforms
                                def prBranch = env.CHANGE_BRANCH ?: env.BRANCH_NAME

                                sh """
                                    sonar-scanner \
                                    -Dsonar.login=${SONAR_AUTH_TOKEN} \
                                    -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                                    -Dsonar.pullrequest.branch=${prBranch} \
                                    -Dsonar.pullrequest.key=${env.CHANGE_ID}
                                """
                            } else {
                                echo "INFO: Branch push detected. Running standard SonarQube branch analysis."
                                sh "sonar-scanner -Dsonar.login=${SONAR_AUTH_TOKEN}"
                            }
                        }
                    }
                }
            }

            stage('Build Project') {
                steps {
                    script {
                        if (config.buildCommands) {
                            echo "Running custom build commands..."
                            config.buildCommands.each { command ->
                                sh command
                            }
                        } else {
                            echo "No build commands provided. Skipping."
                        }
                    }
                }
            }
        }
    }
}
