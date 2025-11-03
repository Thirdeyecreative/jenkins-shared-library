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
            // Credential ID for Bitbucket Git checkout (with username 'devops.thirdeyecreative')
            BITBUCKET_GIT_CREDS_ID = 'devops.thirdeyecreative' 
        }

        stages {
            
            stage('Checkout Code') {
                steps {
                    script {
                        // This is the new, robust check.
                        // The BITBUCKET_GIT_URL variable IS available here.
                        if (env.BITBUCKET_GIT_URL) {
                            
                            // --- IF IT'S A BITBUCKET BUILD ---
                            echo "Bitbucket build detected. Running explicit git checkout."
                            
                            // This is the explicit 'git' step. It bypasses 'scm' completely.
                            git url: env.BITBUCKET_GIT_URL,
                                branch: env.BRANCH_NAME,
                                credentialsId: BITBUCKET_GIT_CREDS_ID
                                refspec: '+refs/heads/*:refs/remotes/origin/*'
                            
                        } else {
                            // --- IF IT'S A GITHUB BUILD ---
                            echo "GitHub build detected. Running standard checkout scm."
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
