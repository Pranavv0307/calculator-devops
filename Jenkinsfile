pipeline {
    agent any

    environment {
        DOCKERHUB_CREDS = credentials('dockerhub-creds')
        IMAGE_NAME = "<your-dockerhub-username>/calculator-devops"
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Push') {
            steps {
                sh "echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin"
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
                sh "docker push ${IMAGE_NAME}:latest"
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker compose down || true'
                sh 'docker compose up -d --build'
                sh 'sleep 15'
                sh 'curl -f http://localhost:8080/actuator/health || exit 1'
            }
        }
    }

    post {
        success {
            mail to: '<your-student-email>',
                 subject: "SUCCESS: Pipeline #${env.BUILD_NUMBER}",
                 body: "The calculator-devops pipeline succeeded. Image: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            mail to: '<your-student-email>',
                 subject: "FAILED: Pipeline #${env.BUILD_NUMBER}",
                 body: "The calculator-devops pipeline failed. Check Jenkins console output."
        }
    }
}
