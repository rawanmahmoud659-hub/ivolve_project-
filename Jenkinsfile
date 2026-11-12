@Library('ivolve-gitops@main') _

pipeline {
    agent { label 'agent1' }
    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
    }
    environment {
        IMAGE_REPO = 'rawanm21/ivolve-app'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }
    stages {
        stage('Checkout') {
            steps { checkout scm }
        }
        stage('Install and Test') {
            steps {
                dir('app') {
                    sh 'npm ci'
                    sh 'npm test --if-present'
                }
            }
        }
        stage('Build and Push Image') {
            steps {
                script { buildAndPushImage(env.IMAGE_REPO, env.IMAGE_TAG) }
            }
        }
        stage('Update GitOps Manifest') {
            steps {
                script { updateGitOpsManifest(env.IMAGE_REPO, env.IMAGE_TAG) }
            }
        }
    }
    post {
        success { echo 'Image published and GitOps manifest updated. Argo CD performs deployment.' }
        failure { echo 'Pipeline failed. Review the stage logs.' }
    }
}
