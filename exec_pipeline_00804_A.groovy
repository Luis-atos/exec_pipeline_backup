//  pipeline tareas_Process
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
if (currentBuild.getBuildCauses().toString().contains('BranchIndexingCause')) { currentBuild.result = 'ABORTED'; return; }
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
       stage('\u2705 Obtener Version'){
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
         stage('\u2705 Entorno'){
            steps{
              // logstash {
               script{
                   def libsEntorno = new divindes.libs.jenkins.functionsEntornos()
                   def libscheckout = new divindes.libs.checkout.checkoutParams()
                   environment_deploy = libsEntorno.eleccionEntorno(env.BRANCH_NAME,pathWS,versionInput)
                   deployTarget=environment_deploy.split("/")[1]
                   echo "--> ****** ${deployTarget}"
                   sleep(15)
                   libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                   echo "Actualizando parametros"
                   sleep(15)
                   if ((deployTarget == "Pre-produccion") || (deployTarget == "Hotfix")){
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
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("HOTFIX(.*)")) }
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
                         def libsBuild = new divindes.libs.build.functionsBuildNewSonar()
                         taskUrl = libsBuild.buildProyectProcess(env.BRANCH_NAME,pathWS,environment_deploy)
                         echo " salida build task Url --> ${taskUrl}"
                         sleep(45)
               }
              // }
            }
         }
          stage('Quality Gates Sonarqube'){
            when{
             anyOf {
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("HOTFIX(.*)")) }
                expression { (env.BRANCH_NAME.matches("feature(.*)")) } 
            }
            }
            steps{
            //   logstash {
               script{
              echo " salida task Url --> ${taskUrl}"
              if (taskUrl!="vacio"){
                userID = currentBuild.getRawBuild().getCauses()[0].getUserId()
               def libsPasaQG = new divindes.libs.build.functionsPasarQG()
               checkQG = libsPasaQG.checkPasaQG(env.JOB_NAME, env.BRANCH_NAME, userID, environment_deploy, versionInput)
               echo "Chequeo --${checkQG}"
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
         //   }
         }
         }
         stage('nexus - Tag'){
            when{
             anyOf {
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
             }
            }
            steps{
           //    logstash {
               script{
                         echo "***********************************************"
                         echo "Nexus : Proceso de Subida y almacenamiento.    "
                         echo "***********************************************"
                         def proyecto_name = env.JOB_NAME.split("/")[0]
                     
                     libsfunctionsTag = new divindes.libs.repo.functionsSubeTag()
                     if (proyecto_name == "REPERMAEST_PROCESS"){
            libsfunctionsArtefactos = new divindes.libs.repo.functionsSubeArtefacto()
            libsfunctionsArtefactos.subirArtefactoTotal(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                     }else{
            libsfunctionsArtefactos = new divindes.libs.repo.functionsSubeArtefactoProcess()
            libsfunctionsArtefactos.subirArtefactoTotalProcess_Gestion(proyecto_name,env.BRANCH_NAME, deployTarget, pathWS,versionInput) 
                     }
                     //libsfunctionsTag = new divindes.libs.repo.functionsSubeTag()
                     libsfunctionsTag.subirNuevoTag(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
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
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("feature(.*)")) }
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                          }
                   }
                   steps {
                   script{
                      echo "Despliegue Desarrollo"
                     if (((deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop'))||
                        ((env.BRANCH_NAME.matches("feature(.*)")))||
                     ((deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")))){
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Desarrollo."
                         echo "*************************************************"
                         def proyecto_name = env.JOB_NAME.split("/")[0]
               if (proyecto_name == "REPERMAEST_PROCESS"){
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcessLinux(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
               }else{
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVALProcess.procesoDespliegueProcess()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcess_raiz(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
               }     
                     }
               
                   }
                   }
                 }
                 stage("Validacion") {
                   when {
                     anyOf {
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("feature(.*)")) }
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
                   script{
                      echo "Despliegue Validacion "
                      if (((deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop'))||
                        ((env.BRANCH_NAME.matches("feature(.*)")))||
                     ((deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")))){
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Validacion."
                         echo "*************************************************"
                     def proyecto_name = env.JOB_NAME.split("/")[0]
                     if (proyecto_name == "REPERMAEST_PROCESS"){
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcessLinux(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                     }else{
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVALProcess.procesoDespliegueProcess()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcess_raiz(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                     }     
                     }
                     
                  //   }
                  
                   }
                   }
                 }
                 stage("Preproduccion") {
                    when {
                     anyOf {
                      expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME == 'develop') }
                      expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
                   //  logstash { 
                     script{
                      echo "Proceso de Pre-Produccion "
                        echo "**************************************************************"
                        echo "Nexus-PreProduccion : Proceso de Bajada Artefacto desde Nexus."
                        echo "**************************************************************"
						  def proyecto_name = env.JOB_NAME.split("/")[0]
                          if (proyecto_name == "REPERMAEST_PROCESS"){
						      libsfunctionsArtefactos = new divindes.libs.repo.functionsDownloadArtefacto()
                              libsfunctionsArtefactos.downloadArtefactoTotal_raiz(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                              sleep(45)
						  }else{
						      libsfunctionsArtefactos = new divindes.libs.repo.functionsDownloadArtefactoProcess()
                              libsfunctionsArtefactos.downloadArtefactoTotalProcess_raiz(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                              sleep(45)
						  }
                         libsfunctionsunzipArtefactos = new divindes.libs.repo.functionsUnzipArtefacto()
                         libsfunctionsunzipArtefactos.unzipArtefacto_raiz(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         echo "*************************************************"
                         echo "Despliegue : Proceso en Pre-Produccion.          "
                         echo "*************************************************"
                       def libscreateReleaseBranch = new divindes.libs.deployPRE.createReleaseBranch()
                       def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                  
                       if (env.BRANCH_NAME.matches("RELEASE(.*)")){
					         echo "*********************************************************"
                        echo "FTP : Proceso en Pre-Produccion. Subida nueva RELEASE****"
                        echo "*********************************************************"
							   echo "*****************************************************"
                        echo "Es una RELEASE : Actualizando a la nueva Version.****"
                        echo "*****************************************************"
					        if (proyecto_name == "REPERMAEST_PROCESS"){
                              libsUpload_ftp_task.upload_ftp_task_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                              libscreateReleaseBranch.newRelease_forTag(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
							}else{
                             libsUpload_ftp_task.upload_ftp_task_process_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                             libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
							} 
                       }else{
					          if (proyecto_name == "REPERMAEST_PROCESS"){
						         libscreateReleaseBranch.newRelease_forTag(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                           libsUpload_ftp_task.upload_ftp_task_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
						      }else{
						         libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                           libsUpload_ftp_task.upload_ftp_task_process_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
						      }
                       } 
                     }
                  //   }
                   }
                 }
                 stage("Produccion") {
                   when {
                     anyOf {
                      expression { (deployTarget == 'Produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
               //  logstash { 
                     script{ 
                     def libsUpdateReleaseBranch = new divindes.libs.deployPRO.UpdateReleaseBranch()
                     def libsmergeBranchMaster = new divindes.libs.deployPRO.mergeToBranch()
                     def libscreateTagBranch = new divindes.libs.deployPRO.createTagBranch()
                     def libsdeleteToRelease = new divindes.libs.deployPRO.deleteToRelease()
                     libsUpdateReleaseBranch.updateRelease(env.BRANCH_NAME, pathWS)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"master")
                     libscreateTagBranch.createTag(env.BRANCH_NAME)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"develop")
                     libsdeleteToRelease.deleteForRelease(env.BRANCH_NAME)
                   }
             //   }
                  }
                 }
                 stage("Hotfix") {
                   when {
                     anyOf {
                      expression {(env.BRANCH_NAME.matches("HOTFIX(.*)")) }
                     }
                   }
                   steps {
               //  logstash { 
                     script{
                      echo "*********************************************************"
                      echo "HOTFIX : Proceso en HOTFIX. *****************************"
                      echo "*********************************************************" 
                      def libsOpeBranchHotfix = new divindes.libs.deployHOTFIX.mergeHotfixToBranch()
                      libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"develop")
                      sleep(15)
                      libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"master")
                      sleep(15)
                      def functionsUniqueRelease = new divindes.libs.deployPRE.functionscheckNumReleases()
                      def num_rama_release = functionsUniqueRelease.checkTotalRamaRelease()
                          echo "*********************************************************"
                          echo "HOTFIX : Numeo RELEASE ${num_rama_release}. ****************"
                          echo "*********************************************************" 
                      if (num_rama_release == '1'){
                          def libsExistsReleaseBranch = new divindes.libs.deployPRE.functions_commons()
                          def ExistsReleaseBranch = libsExistsReleaseBranch.remote_branch_version()
                          echo "*********************************************************"
                          echo "HOTFIX : RELEASE ${ExistsReleaseBranch}. ****************"
                          echo "*********************************************************" 
                          libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"RELEASE-${ExistsReleaseBranch}")
                      }
                      def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                      libsUpload_ftp_task.upload_ftp_task(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                      libsOpeBranchHotfix.creaTagFromHotfix(env.BRANCH_NAME)
                      libsOpeBranchHotfix.deleteBranchHotfix(env.BRANCH_NAME)
                      
                      
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
                  libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut_HTML()
                  libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL,pathWS)
                  echo "***** Borrando Workspace ************ "
                  cleanWs()
                 // libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut()
                 // libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL,pathWS)
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


================================================
//  pipeline tareas_Process
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
if (currentBuild.getBuildCauses().toString().contains('BranchIndexingCause')) { currentBuild.result = 'ABORTED'; return; }
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
       stage('\u2705 Obtener Version'){
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
         stage('\u2705 Entorno'){
            steps{
              // logstash {
               script{
                   def libsEntorno = new divindes.libs.jenkins.functionsEntornos()
                   def libscheckout = new divindes.libs.checkout.checkoutParams()
                   environment_deploy = libsEntorno.eleccionEntorno(env.BRANCH_NAME,pathWS,versionInput)
                   deployTarget=environment_deploy.split("/")[1]
                   echo "--> ****** ${deployTarget}"
                   sleep(15)
                   libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                   echo "Actualizando parametros"
                   sleep(15)
                   if ((deployTarget == "Pre-produccion") || (deployTarget == "Hotfix")){
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
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("HOTFIX(.*)")) }
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
                         def libsBuild = new divindes.libs.build.functionsBuildNewSonar()
                         taskUrl = libsBuild.buildProyectProcess(env.BRANCH_NAME,pathWS,environment_deploy)
                         echo " salida build task Url --> ${taskUrl}"
                         sleep(45)
               }
              // }
            }
         }
          stage('Quality Gates Sonarqube'){
            when{
             anyOf {
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                expression { (env.BRANCH_NAME.matches("HOTFIX(.*)")) }
                expression { (env.BRANCH_NAME.matches("feature(.*)")) } 
            }
            }
            steps{
            //   logstash {
               script{
              echo " salida task Url --> ${taskUrl}"
              if (taskUrl!="vacio"){
                userID = currentBuild.getRawBuild().getCauses()[0].getUserId()
               def libsPasaQG = new divindes.libs.build.functionsPasarQG()
               checkQG = libsPasaQG.checkPasaQG(env.JOB_NAME, env.BRANCH_NAME, userID, environment_deploy, versionInput)
               echo "Chequeo --${checkQG}"
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
         //   }
         }
         }
         stage('nexus - Tag'){
            when{
             anyOf {
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
             }
            }
            steps{
           //    logstash {
               script{
                         echo "***********************************************"
                         echo "Nexus : Proceso de Subida y almacenamiento.    "
                         echo "***********************************************"
                         def proyecto_name = env.JOB_NAME.split("/")[0]
                     libsfunctionsArtefactos = new divindes.libs.repo.functionsSubeArtefactoProcess()
                     libsfunctionsTag = new divindes.libs.repo.functionsSubeTag()
                     if (proyecto_name == "REPERMAEST_PROCESS"){
            libsfunctionsArtefactos.subirArtefactoTotal(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                     }else{
            libsfunctionsArtefactos.subirArtefactoTotalProcess_Gestion(proyecto_name,env.BRANCH_NAME, deployTarget, pathWS,versionInput) 
                     }
                     }
                     
                     //libsfunctionsTag = new divindes.libs.repo.functionsSubeTag()
                     libsfunctionsTag.subirNuevoTag(env.BRANCH_NAME, deployTarget, pathWS,versionInput)

             //  }
            }
         }

        stage('entorno Deploy'){
           stages {
                stage("Desarrollo") {
                   when {
                        anyOf {
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop') }
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("feature(.*)")) }
                              expression { (deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                          }
                   }
                   steps {
                   script{
                      echo "Despliegue Desarrollo"
                     if (((deployTarget == 'Desarrollo') && (env.BRANCH_NAME == 'develop'))||
                        ((env.BRANCH_NAME.matches("feature(.*)")))||
                     ((deployTarget == 'Desarrollo') && (env.BRANCH_NAME.matches("RELEASE(.*)")))){
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Desarrollo."
                         echo "*************************************************"
                         def proyecto_name = env.JOB_NAME.split("/")[0]
               if (proyecto_name == "REPERMAEST_PROCESS"){
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcessLinux(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
               }else{
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVALProcess.procesoDespliegueProcess()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcess_raiz(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
               }     
                     }
               
                   }
                   }
                 }
                 stage("Validacion") {
                   when {
                     anyOf {
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop') }
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("feature(.*)")) }
                      expression { (deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
                   script{
                      echo "Despliegue Validacion "
                      if (((deployTarget == 'Validacion') && (env.BRANCH_NAME == 'develop'))||
                        ((env.BRANCH_NAME.matches("feature(.*)")))||
                     ((deployTarget == 'Validacion') && (env.BRANCH_NAME.matches("RELEASE(.*)")))){
                         echo "*************************************************"
                         echo "Despliegue : Proceso de Despliegue en Validacion."
                         echo "*************************************************"
                     def proyecto_name = env.JOB_NAME.split("/")[0]
                     if (proyecto_name == "REPERMAEST_PROCESS"){
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVAL.procesoDespliegue()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcessLinux(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                     }else{
def libsProcesoDespliegueDESVAL = new divindes.libs.DespliegueDESVALProcess.procesoDespliegueProcess()
libsProcesoDespliegueDESVAL.etapasDespliegueDESVALProcess_raiz(env.JOB_NAME, env.BRANCH_NAME,pathWS,deployTarget)
                     }     
                     }
                     
                  //   }
                  
                   }
                   }
                 }
                 stage("Preproduccion") {
                    when {
                     anyOf {
                      expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME == 'develop') }
                      expression { (deployTarget == 'Pre-produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
                   //  logstash { 
                     script{
                       echo "Proceso de Pre-Produccion "
                        echo "**************************************************************"
                        echo "Nexus-PreProduccion : Proceso de Bajada Artefacto desde Nexus."
                        echo "**************************************************************"
                         libsfunctionsArtefactos = new divindes.libs.repo.functionsDownloadArtefactoProcess()
                         libsfunctionsArtefactos.downloadArtefactoTotalProcess_raiz(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(45)
                         libsfunctionsunzipArtefactos = new divindes.libs.repo.functionsUnzipArtefacto()
                         libsfunctionsunzipArtefactos.unzipArtefacto_raiz(env.BRANCH_NAME, deployTarget, pathWS,versionInput)
                         sleep(10)
                         echo "*************************************************"
                         echo "Despliegue : Proceso en Pre-Produccion.          "
                         echo "*************************************************"
                       def libscreateReleaseBranch = new divindes.libs.deployPRE.createReleaseBranch()
                       def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                  
                       if (env.BRANCH_NAME.matches("RELEASE(.*)")){
                             echo "*********************************************************"
                             echo "FTP : Proceso en Pre-Produccion. Subida nueva RELEASE****"
                             echo "*********************************************************"
                             libsUpload_ftp_task.upload_ftp_task_process_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                             echo "*****************************************************"
                             echo "Es una RELEASE : Actualizando a la nueva Version.****"
                             echo "*****************************************************"
                             libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                       }else{
                         libscreateReleaseBranch.newRelease(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                         libsUpload_ftp_task.upload_ftp_task_process_raiz(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                       }
                     }
                  //   }
                   }
                 }
                 stage("Produccion") {
                   when {
                     anyOf {
                      expression { (deployTarget == 'Produccion') && (env.BRANCH_NAME.matches("RELEASE(.*)")) }
                     }
                   }
                   steps {
               //  logstash { 
                     script{ 
                     def libsUpdateReleaseBranch = new divindes.libs.deployPRO.UpdateReleaseBranch()
                     def libsmergeBranchMaster = new divindes.libs.deployPRO.mergeToBranch()
                     def libscreateTagBranch = new divindes.libs.deployPRO.createTagBranch()
                     def libsdeleteToRelease = new divindes.libs.deployPRO.deleteToRelease()
                     libsUpdateReleaseBranch.updateRelease(env.BRANCH_NAME, pathWS)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"master")
                     libscreateTagBranch.createTag(env.BRANCH_NAME)
                     libsmergeBranchMaster.mergeForBranch(env.BRANCH_NAME,"develop")
                     libsdeleteToRelease.deleteForRelease(env.BRANCH_NAME)
                   }
             //   }
                  }
                 }
                 stage("Hotfix") {
                   when {
                     anyOf {
                      expression {(env.BRANCH_NAME.matches("HOTFIX(.*)")) }
                     }
                   }
                   steps {
               //  logstash { 
                     script{
                      echo "*********************************************************"
                      echo "HOTFIX : Proceso en HOTFIX. *****************************"
                      echo "*********************************************************" 
                      def libsOpeBranchHotfix = new divindes.libs.deployHOTFIX.mergeHotfixToBranch()
                      libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"develop")
                      sleep(15)
                      libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"master")
                      sleep(15)
                      def functionsUniqueRelease = new divindes.libs.deployPRE.functionscheckNumReleases()
                      def num_rama_release = functionsUniqueRelease.checkTotalRamaRelease()
                          echo "*********************************************************"
                          echo "HOTFIX : Numeo RELEASE ${num_rama_release}. ****************"
                          echo "*********************************************************" 
                      if (num_rama_release == '1'){
                          def libsExistsReleaseBranch = new divindes.libs.deployPRE.functions_commons()
                          def ExistsReleaseBranch = libsExistsReleaseBranch.remote_branch_version()
                          echo "*********************************************************"
                          echo "HOTFIX : RELEASE ${ExistsReleaseBranch}. ****************"
                          echo "*********************************************************" 
                          libsOpeBranchHotfix.mergeHotfixForBranch(env.BRANCH_NAME,"RELEASE-${ExistsReleaseBranch}")
                      }
                      def libsUpload_ftp_task = new divindes.libs.deployPRE.functionsUpload_ftp_task()
                      libsUpload_ftp_task.upload_ftp_task(env.BRANCH_NAME, env.JOB_NAME,versionInput,pathWS)
                      libsOpeBranchHotfix.creaTagFromHotfix(env.BRANCH_NAME)
                      libsOpeBranchHotfix.deleteBranchHotfix(env.BRANCH_NAME)
                      
                      
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
                  libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut_HTML()
                  libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL,pathWS)
                  echo "***** Borrando Workspace ************ "
                  cleanWs()
                 // libsfunctionsZipConsoleOut = new divindes.libs.report.functionsZipConsoleOut()
                 // libsfunctionsZipConsoleOut.creaZIPConsoleOut(env.JOB_NAME,env.BUILD_NUMBER,env.BUILD_URL,pathWS)
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
