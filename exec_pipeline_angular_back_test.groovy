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
def agentLabels='linux'
if (currentBuild.getBuildCauses().toString().contains('BranchIndexingCause')) { currentBuild.result = 'ABORTED'; return; }
if ((env.BRANCH_NAME.matches("feature(.*)"))){
   agentLabels='Jenkins_CI'
}
pipeline{
   agent { label agentLabels}
   
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
               /*
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
                  
                  def libscheckout = new divindes.libs.checkout_Front.checkoutParams_Front()
                  libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                  */
                  pathWS = pwd()
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
                   def libscheckout = new divindes.libs.checkout_Front.checkoutParams_Front()
                   environment_deploy = libsEntorno.eleccionEntorno(env.BRANCH_NAME,pathWS,versionInput)
                   deployTarget=environment_deploy.split("/")[1]
                   echo "--> ****** ${deployTarget}"
                   sleep(15)        
                   libscheckout.paramsDownload(pathWS, env.JOB_NAME)
                   echo "Actualizando parametros"
                   sleep(15)
                   /*
                   if ((deployTarget == "Pre-produccion") || (deployTarget == "Hotfix")){
                     echo "*************************************" 
                     echo "**Creando Carpetas destino al FTP ***"
                     echo "*************************************" 
                     libscheckout.fichSHDownload(pathWS, 'ficheros_SH')
                     def libsCarpetasPermisosSH = new divindes.libs.jenkins.carpetasPermisosSH()
                     libsCarpetasPermisosSH.permisosCarpetasSH(env.BRANCH_NAME,pathWS) 
                   }
                   */
               }
               //}
            }
         }
         	stage('execute mvn') {
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
               steps {
                    script{
                    echo "************************************************"
                    echo "Construccion : Proceso de Construccion. ********"
                    echo "************************************************"   
                    
                    }
               }
        	}

}
}
}

return this
