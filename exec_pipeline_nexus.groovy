// Fichero que contiene el pipeline común a todos los proyectos
import groovy.transform.Field
import java.text.SimpleDateFormat
import java.util.zip.ZipFile

def call(Map params) {
def pathWS=''
def userID =""
def versionInput=""
def environment_deploy=""
def deployTarget=""
def versionOK=""
def taskUrl =""
def statusSonar="PASSED"
def libsfunctionsArtefactos
def libsfunctionsZipConsoleOut
def checkQG=""
pipeline{
   agent any
   
   options {
    timeout(time: 2, unit: 'HOURS')   // timeout on whole pipeline job
   }
   tools{
	    maven "apache-maven-3.8.6"
	   jdk "openjdk1.8"
	 }
         
   stages{  
         stage('Download params'){
         steps{
         //   logstash {
            script{
                  def sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
                  echo "Fecha de inicio lanzamiento: " + sdf.format(new Date())
                  try{
                   // userID = currentBuild.getRawBuild().getCauses()[0].getUserId()
                     userID = currentBuild.getRawBuild().getCauses()[0].getId()
                    echo "Usuario ID: ${userID}"
                  }catch (e){
                   userID = "ERROR"
                   println ("Error al recuperar el usuario que lanza el JOB")
                  }  

                  pathWS = pwd()
                  echo "entrando chequeo parametros" 
                  def libscheckout = new divindes.libs.checkout.checkoutParams()
                  libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                  libscheckout.fichSHDownload(pathWS, 'ficheros_SH')
                  echo "Saliendo chequeo parametros" 
                  /*
                  build job: 'PIPELINE_CHECKPARAMS', wait: false, parameters: [
                  string(name: "job_name",value: env.JOB_NAME),
                  string(name: "path_params",value: pathWS),
                  ]
                  */
            }
         //   }
          }
       }
       stage('Obtener Version'){
            steps{
            //   logstash {
               script{
                   def libsObtenVersion = new divindes.libs.jenkins.functionsVersionInput()
                   def checkVersion = new divindes.libs.jenkins.functionsCheckVersionValid()
                   versionInput = libsObtenVersion.obtenerVersion(env.BRANCH_NAME, env.JOB_NAME)
                    echo "versionInput: ${versionInput}"
                   checkVersion.checkVersionValid(env.BRANCH_NAME, env.JOB_NAME, versionInput)
               }
            //   }
            }
         }
         stage('Entorno'){
            steps{
              // logstash {
               script{
                   def libsEntorno = new divindes.libs.jenkins.functionsEntornos()
                   environment_deploy = libsEntorno.eleccionEntorno(env.BRANCH_NAME,pathWS)
                   deployTarget=environment_deploy.split("/")[1]
               }
               //}
            }
         }
         stage('build & Sonarqube'){
            tools{
              jdk "openjdk1.8"
	         }
            when{
               allOf {
                expression { deployTarget != 'Produccion' }
                expression { deployTarget != 'Validacion' }
               }
            }
            steps{
              // logstash {
               script{        
                   echo "**************************************************"
                   echo "      Proceso de Build &  Sonarqube.              "
                   echo "**************************************************"
exec_mvn = "export JAVA_HOME='/usr/java/jdk1.8.0_321-amd64'; mvn -U -f source/pom.xml clean package -s /opt/apache-maven-3.8.6/conf/settings_new.xml"
output_mvn = sh(script: exec_mvn, returnStdout: true)
echo "${output_mvn}"

             
            echo "**************************************************"
            echo "      Proceso de   Sonarqube.                     "
            echo "**************************************************"

           
               }
              // }
            }
         }
          stage('Quality Gates Sonarqube'){
            when{
               allOf {
                expression { deployTarget != 'Produccion' }
                expression { deployTarget != 'Validacion' }
                expression { taskUrl != "vacio" }
               }
            }
            steps{
            //   logstash {
               script{
              echo "Quality Gates Sonarqube: salida task Url "
              
                   echo "**************************************************"
                   echo "Quality Gates Sonarqube : Quality Gates Sonarqube."
                   echo "**************************************************"
              
             }
         //   }
         }
         }
         stage('nexus'){
            steps{
           //    logstash {
               script{
                   echo "**************************************************"
                   echo "               NEXUS : NEXUS.                     "
                   echo "**************************************************"
               }
             //  }
            }
         }

        stage('entorno Deploy'){
           stages {
                
                stage("Desarrollo") {
                   when {
                      allOf{
                         expression { deployTarget == 'Desarrollo' }
                     //    expression { statusSonar == 'PASSED' || statusSonar == 'PENDING' }
                      }
                   }
                   steps {
                   script{
                      echo "Despliegue Desarrollo"
                   }
                   }
                 }
                 stage("Validacion") {
                   when {
                      expression { deployTarget == 'Validacion' }
                   }
                   steps {
                   script{
                      echo "Despliegue Validacion "
                   }
                   }
                 }
                 stage("Preproduccion") {
                   when {
                      expression { deployTarget == 'Pre-produccion' }
                   }
                   steps {
                   //  logstash { 
                     script{
                         echo "***** Preproduccion "
                     }
                  //   }
                   }
                 }
                 stage("Produccion") {
                   when {
                      expression { deployTarget == 'Produccion' }
                   }
                   steps {
               //  logstash { 
                     script{ 
                         echo "***** PRODUCCION *********** "
                   }
             //   }
                  }
                 }
            }
            
         }

    } 
    post{
        always {
         //  steps{
           //    logstash {
               script{
                  libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut()
                  libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL)
                echo "Hello always!"
               }
             //  }
          //  }

        }
        success{
           //  steps{
           //    logstash {
               script{
                
                      echo "Hello nexus finaly!"

               }
             //  }
           // }
        }
        failure {
            echo "Mensaje de fallo"
        }
    }         
      
  }

}
return this
