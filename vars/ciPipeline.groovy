// This is the corrected, final version of your shared library script
def call(Map config = [:]) {
    pipeline {
        agent { label 'debian-master' }

        environment {
            SONAR_AUTH_TOKEN = credentials('sonar-global-token')
        }

        stages {
            // The initial checkout is handled by the multibranch pipeline itself,
            // so we don't need a separate checkout stage here.
            // Using 'checkout scm' ensures we have the latest code.
            
            stage('SonarQube Analysis') {
                steps {
                    // It's good practice to ensure the workspace has the correct code
                    // before running analysis.
                    checkout scm

                    script {
                        withSonarQubeEnv('MySonarQube') {
                            if (env.CHANGE_ID) {
                                echo "INFO: Pull Request build detected. Running SonarQube PR analysis."
                                sh """
                                    sonar-scanner \
                                    -Dsonar.login=${SONAR_AUTH_TOKEN} \
                                    -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                                    -Dsonar.pullrequest.branch=${env.BRANCH_NAME} \
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