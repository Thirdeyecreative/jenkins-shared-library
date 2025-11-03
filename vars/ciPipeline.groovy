// This is the universal script for both Bitbucket and GitHub
def call(Map config = [:]) {
    pipeline {
        agent { label 'debian-master' }

        // *** THIS IS THE NEW, CRITICAL FIX ***
        options {
            // This stops Jenkins from running the implicit 'Checkout SCM'
            // that was using the wrong credential (the API one) and hanging.
            skipDefaultCheckout true
        }
        // ************************************

        environment {
            SONAR_AUTH_TOKEN = credentials('sonar-global-token')
            // Credential ID for Bitbucket Git checkout
            // GitHub builds will just ignore this.
            BITBUCKET_GIT_CREDS_ID = 'devops.thirdeyecreative' 
        }

        stages {
            
            stage('Checkout Code') { // Our explicit checkout will run instead
                steps {
                    script {
                        if (env.BITBUCKET_GIT_URL) {
                            // --- IF IT'S A BITBUCKET BUILD ---
                            echo "Bitbucket build detected. Running explicit checkout..."
                            checkout([
                                $class: 'GitSCM',
                                // The plugin provides these variables for us
                                userRemoteConfigs: [[
                                    url: env.BITBUCKET_GIT_URL, 
                                    credentialsId: BITBUCKET_GIT_CREDS_ID
                                ]],
                                branches: [[name: env.BRANCH_NAME]] 
                            ])
                        } else {
                            // --- IF IT'S A GITHUB BUILD ---
                            echo "GitHub build detected. Running standard checkout scm."
                            // This will run for your GitHub builds
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
