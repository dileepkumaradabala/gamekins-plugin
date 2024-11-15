pipeline {
    agent any
    tools {
        maven 'Maven_Name_From_Tool_Configuration'
    }
    stages {
        stage('Build') {
            steps {
                sh 'mvn -B clean compile'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
        }
        stage('Package') {
            steps {
                sh 'mvn -B hpi:hpi'
            }
        }
    }
}
