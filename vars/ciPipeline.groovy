// Universal Jenkins shared library script for both Bitbucket & GitHub
def call(Map config = [:]) {
    pipeline {
        agent { label 'debian-master' }

        options {
            skipDefaultCheckout true
        }

        environment {
            SONAR_AUTH_TOKEN = credentials('sonar-global-token')
            // Use unified credential for Bitbucket
            BITBUCKET_GIT_CREDS_ID = 'bitbucket-api-scan-creds'
        }

        stages {

            stage('Checkout Code') {
                steps {
                    script {
                        // --- Detect Bitbucket or GitHub context ---
                        if (env.BITBUCKET_REPO_OWNER || env.BITBUCKET_GIT_HTTP_ORIGIN || env.BITBUCKET_GIT_URL) {
                            echo "Bitbucket build detected. Running explicit checkout..."

                            // Fall back gracefully if env.BITBUCKET_GIT_URL is missing
                            def repoUrl = env.BITBUCKET_GIT_URL ?: "https://bitbucket.org/${env.BITBUCKET_REPO_OWNER}/${env.BITBUCKET_REPO_SLUG}.git"
                            def branchName = env.BRANCH_NAME ?: "main"

                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: "*/${branchName}"]],
                                userRemoteConfigs: [[
                                    url: repoUrl,
                                    credentialsId: BITBUCKET_GIT_CREDS_ID
                                ]]
                            ])

                        } else {
                            echo "GitHub build detected. Using default checkout scm."
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
                                echo "PR build detected. Running SonarQube PR analysis..."
                                def prBranch = env.CHANGE_BRANCH ?: env.BRANCH_NAME
                                sh """
                                    sonar-scanner \
                                    -Dsonar.login=${SONAR_AUTH_TOKEN} \
                                    -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                                    -Dsonar.pullrequest.branch=${prBranch} \
                                    -Dsonar.pullrequest.key=${env.CHANGE_ID}
                                """
                            } else {
                                echo "Branch build detected. Running standard SonarQube analysis..."
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
                            config.buildCommands.each { cmd -> sh cmd }
                        } else {
                            echo "No build commands provided. Skipping build stage."
                        }
                    }
                }
            }
        }
    }
}
