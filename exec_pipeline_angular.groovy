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
                  echo "Saliendo chequeo parametros"
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
                   def libscheckout = new divindes.libs.checkout.checkoutParams()
                   environment_deploy = libsEntorno.eleccionEntorno(env.BRANCH_NAME,pathWS,versionInput)
                   deployTarget=environment_deploy.split("/")[1]
                   echo "--> ****** ${deployTarget}"
                   sleep(15)
                   if ((deployTarget == "Validacion") || (deployTarget == "Pre-produccion")){
                   //def libscheckout = new divindes.libs.checkout.checkoutParams()
                   libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                   echo "Actualizando parametros"
                   sleep(15)
                   }
                   if (deployTarget == "Pre-produccion"){
                     echo "*************************************" 
                     echo "**Creando Carpetas destino al FTP ***"
                     echo "*************************************" 
                     //def libscheckout = new divindes.libs.checkout.checkoutParams()
                     libscheckout.fichSHDownload(pathWS, 'ficheros_SH')
                     def libsCarpetasPermisosSH = new divindes.libs.jenkins.carpetasPermisosSH()
                     libsCarpetasPermisosSH.permisosCarpetasSH(env.BRANCH_NAME,pathWS) 
                   }
               }
               //}
            }
         }
         stage('build & Sonarqube'){
            tools{
              jdk "openjdk-1.8"
	         }
            when{
               anyOf {
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("feature(.*)")) }
                
               }
            }
            steps{
              // logstash {
               script{
                         echo "***********************************************"
                         echo "Construccion : Proceso de Construccion. ********"
                         echo "************************************************"      
                         echo "--> dentro entorno ${deployTarget}"
                        // def libsBuild = new divindes.libs.build.functionsBuildNewSonar()
                        // taskUrl = libsBuild.buildProyect(env.BRANCH_NAME,pathWS,environment_deploy)
                        // echo " salida build task Url --> ${taskUrl}"
               }
              // }
            }
         }
          stage('Quality Gates Sonarqube'){
            when{
             anyOf {
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("feature(.*)")) }  
             }
            }
            steps{
            //   logstash {
               script{
                   echo "***********************************************"
                   echo "Quality Gates Sonarqube : Sonarqube            "
                   echo "***********************************************"

/*
              echo " salida task Url --> ${taskUrl}"
              if (taskUrl!="vacio"){
                userID = currentBuild.getRawBuild().getCauses()[0].getUserId()
               def libsPasaQG = new divindes.libs.build.functionsPasarQG()
               checkQG = libsPasaQG.checkPasaQG(env.JOB_NAME, env.BRANCH_NAME, userID, environment_deploy, versionInput)
         if (checkQG == "NO_SALTOQG"){
               //def libSonar = new divindes.libs.sonar.functionsQualityGatesSonar9()
               def libSonar = new divindes.libs.sonar.functionsQualityGates()
               resultCurrent = libSonar.QualityGates(environment_deploy, taskUrl)
      if ((deployTarget.equals("Desarrollo")) && (resultCurrent == "FAILURE") || (deployTarget.equals("Desarrollo")) && (resultCurrent == "ERROR")){
         catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            sh "exit 1"
         }
      }else{
         try{
            if ((resultCurrent == 'FAILURE') || (resultCurrent == 'ABORTED') || (resultCurrent == "ERROR")){
               Integer.parseInt( "hola" )
            }
         }catch (err){
            echo "ERROR : No supera los Quality-Gates. Revisar errores de Sonarqube"
            throw new Exception("ERROR : No supera los Quality-Gates. Revisar errores de Sonarqube")
         }
      }
      }else{
            echo "QG Condicionado : No analizado el Quality-Gates."
         }
               }else{
                   echo "***********************************************"
                   echo "QG No Activado : No analizado el Quality-Gates."
                   echo "***********************************************"
               }
             }
*/
         //   }
         }
         }
         stage('nexus'){
            when{
             anyOf {
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
             }
            }
            steps{
           //    logstash {
               script{
                         echo "***********************************************"
                         echo "Nexus : Proceso de Subida y almacenamiento."
                         echo "***********************************************"
                    // libsfunctionsArtefactos = new divindes.libs.repo.functionsSubeArtefacto()
                    // libsfunctionsArtefactos.subirArtefacto(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
               }
             //  }
            }
         }

        stage('entorno Deploy'){
           stages {
                stage("Desarrollo") {
                   when {
                        anyOf {
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                          }
                   }
                   steps {
                   script{
                      echo "Despliegue Desarrollo"
/*
                      if (((deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop'))||
                     ((deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")))){
                        echo "*************************************************"
                         echo "Nexus : Proceso de Bajada Artefacto desde Nexus."
                         echo "************************************************"
                         libsfunctionsArtefactos = new divindes.libs.repo.functionsDownloadArtefacto()
                         libsfunctionsArtefactos.downloadArtefacto(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         libsfunctionsunzipArtefactos = new divindes.libs.repo.functionsUnzipArtefacto()
                         libsfunctionsunzipArtefactos.unzipArtefacto(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Desarrollo."
                         echo "*************************************************"
                         def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
                         libsProcesoDespliegueDESVAL.etapasDespliegueDESVAL(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                  
                     }
*/
                   }
                   }
                 }
                 stage("Validacion") {
                   when {
                     anyOf {
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
                   script{
                      echo "Despliegue Validacion "
                      /*
                        echo "***********************************************************"
                        echo "Nexus-Validacion : Proceso de Bajada Artefacto desde Nexus."
                        echo "***********************************************************"
                         libsfunctionsArtefactos = new divindes.libs.repo.functionsDownloadArtefacto()
                         libsfunctionsArtefactos.downloadArtefacto(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         libsfunctionsunzipArtefactos = new divindes.libs.repo.functionsUnzipArtefacto()
                         libsfunctionsunzipArtefactos.unzipArtefacto(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Validacion."
                         echo "*************************************************"
                         def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
                         libsProcesoDespliegueDESVAL.etapasDespliegueDESVAL(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                      */
                  //   }
                  
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
                       def libscreateReleaseBranch = new divindes.libs.deployPRE.createReleaseBranch()
                       def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                       libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
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

    } 
    post{
        always {
         //  steps{
           //    logstash {
               script{
                  libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut()
                  libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL,pathWS)
               }
             //  }
          //  }

        }
        success{
           //  steps{
           //    logstash {
               script{
                
                     echo "*************************************************"
                     echo "Jenkins : Proceso finaliza correctamente.        "
                     echo "*************************************************"
               }
             //  }
           // }
        }
        failure {
                     echo "*************************************************"
                     echo "Jenkins : Proceso finaliza con Errores.          "
                     echo "*************************************************"
        }
    }         
      
  }

}
return this
