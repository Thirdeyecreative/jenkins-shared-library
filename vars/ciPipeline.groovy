// This is the universal script for both Bitbucket and GitHub
def call(Map config = [:]) {
    pipeline {
        agent { label 'debian-master' }

        environment {
            SONAR_AUTH_TOKEN = credentials('sonar-global-token')
            // Credential ID for Bitbucket Git checkout
            // GitHub builds will just ignore this.
            BITBUCKET_GIT_CREDS_ID = 'devops.thirdeyecreative' 
        }

        stages {
            
            stage('Checkout Code') {
                steps {
                    script {
                        // This is the new SCM-detection logic
                        if (env.BITBUCKET_GIT_URL) {
                            // --- IF IT'S A BITBUCKET BUILD ---
                            echo "Bitbucket build detected. Running explicit checkout..."
                            checkout([
                                $class: 'GitSCM',
                                userRemoteConfigs: [[
                                    url: env.BITBUCKET_GIT_URL, 
                                    credentialsId: BITBUCKET_GIT_CREDS_ID
                                ]],
                                branches: [[name: env.BRANCH_NAME]] 
                            ])
                        } else {
                            // --- IF IT'S A GITHUB BUILD ---
                            echo "GitHub build detected. Running standard checkout scm."
                            // This is the original command that works for GitHub
                            checkout scm
                        }
                    }
                }
            }

            stage('SonarQube Analysis') {
                steps {
                    // The 'checkout scm' command is no longer needed here
                    // because the code is already checked out from the stage above.
                    script {
                        withSonarQubeEnv('MySonarQube') {
                            if (env.CHANGE_ID) {
                                echo "INFO: Pull Request build detected. Running SonarQube PR analysis."
                                
                                // This logic correctly finds the PR branch name for BOTH platforms
                                // Bitbucket uses CHANGE_BRANCH
                                // GitHub uses BRANCH_NAME
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
