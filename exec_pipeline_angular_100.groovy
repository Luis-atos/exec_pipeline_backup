// Fichero que contiene el pipeline común a todos los proyectos
// vars/exec_pipeline.groovy
import groovy.transform.Field
import java.text.SimpleDateFormat



def call(Map params) {
def pathWS=''
def versionInput=""
def environment_deploy=""
def deployTarget=""
def versionOK=""
def taskUrl =""
def statusSonar="PASSED"
def libsfunctionsArtefactos
def libsfunctionsZipConsoleOut
pipeline{
   agent any
   
   tools{
	   jdk "jdk1.8.0_321"
	 }
         
   stages{
          stage('Download app_branch'){
            steps{
            //   logstash {
               script{
                  echo """*************** Parametros de entrada *************************
                                Tecnologia: ${params.Tecnologia}
                                ExisteBD: ${params.ExisteBD}
                                ExisteRPT: ${params.ExisteRPT}
                                LanzarSonar: ${params.LanzarSonar}
                                RutaPom: ${params.RutaPom}
                                GIT_URL: ${params.GIT_URL}                     
                                """
                  pathWS = pwd()
               }
               }
            //}
         }   
         stage('Download params'){
         steps{
          //  logstash {
            script{
                  build job: 'PIPELINE_CHECKPARAMS', wait: false, parameters: [
                  string(name: "job_name",value: env.JOB_NAME),
                  string(name: "path_params",value: pathWS),
                  ]
            }
            }
         // }
       }

       stage('Obtener Version'){
            steps{
              // logstash {
               script{
                   def libsObtenVersion = new divindes.libs.jenkins.functionsVersionInput()
                   def checkVersion = new divindes.libs.jenkins.functionsCheckVersionValid()
                   versionInput = libsObtenVersion.obtenerVersion(env.BRANCH_NAME, env.JOB_NAME)
                   checkVersion.checkVersionValid(env.BRANCH_NAME, env.JOB_NAME, versionInput)
               }
             //  }
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
            when{
               expression { deployTarget != 'Produccion' }
            }
            steps{
              // logstash {
               script{
                   echo "--> dentro entorno ${deployTarget}"
                   def libsBuild = new divindes.libs.build.functionsBuildNpm()
                   libsBuild.install_binary_path(pathWS)
                   taskUrl = libsBuild.build_and_sonarqube_task_mod(env.BRANCH_NAME,pathWS,environment_deploy) 
                  // def desplegar = new Deploy()
                  // desplegar.deploy_develop_ftp(this)

/*
taskUrl = this."${params.Tecnologia}".build_and_sonarqube_task_mod(jobBaseName, params.LanzarSonar, params.RutaPackage, env.ENTORNO,env.BRANCH_NAME,proyKey, sonarprojectName,params.NomDespliegue[0],params.NomDespliegue[1]);
*/
                    }

              // }
            }
         }
          stage('Quality Gates Sonarqube'){
            when{
               expression { deployTarget != 'Produccion' }
            } 
            steps{
            //   logstash {
               script{
              echo "qualityGates"
              // }
            }
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
                     echo "Hello desa!"
                      echo "Hello desa!"
                       echo "Hello desa!"
                     //  def desplegar = new Deploy()
                     //  desplegar.deploy_develop_ftp(this)

                   }
                 }
                 stage("Validacion") {
                   when {
                      expression { deployTarget == 'Validacion' }
                   }
                   steps {
                     echo "Hello valida!"
                   }
                 }
                 stage("Preproduccion") {
                   when {
                      expression { deployTarget == 'Pre-produccion' }
                   }
                   steps {
                   //  logstash { 
                     script{
                       def libscreateReleaseBranch = new divindes.libs.deployPRE.createReleaseBranch()
                       def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                       libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput)
                       libsUpload_ftp_task.upload_ftp_task(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
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
                     def libsUpdateReleaseBranch = new divindes.libs.deployPRO.UpdateReleaseBranch()
                     def libsmergeBranchMaster = new divindes.libs.deployPRO.mergeToBranch()
                     def libscreateTagBranch = new divindes.libs.deployPRO.createTagBranch()
                     def libsdeleteToRelease = new divindes.libs.deployPRO.deleteToRelease()
                     libsUpdateReleaseBranch.updateRelease(env.BRANCH_NAME)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"master")
                     libscreateTagBranch.createTag(env.BRANCH_NAME)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"develop")
                     libsdeleteToRelease.deleteForRelease(env.BRANCH_NAME)
                   }
             //   }
                  }
                 }
            }
            
         }

         stage('nexus'){
            when {
               expression { deployTarget == 'Desarrollo' }
            }
            steps{
           //    logstash {
               script{
                   
                      echo "Hello Dentro nexus!"

               }
             //  }
            }
         }
         
         stage('send Report'){
            steps{
           //    logstash {
               script{
                  
                 echo "Hello send Report!"
               }
             //  }
            }
         }

    } 
    post{
        always {
         //  steps{
           //    logstash {
               script{
                
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
