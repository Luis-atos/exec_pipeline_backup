// Fichero que contiene el pipeline común a todos los proyectos
// vars/exec_pipeline.groovy
import groovy.transform.Field
import java.text.SimpleDateFormat

def jobBaseName = ""
def MAJOR = ""
def MINOR = ""
def PATCH = ""

def taskUrl = ""
def PerfilPomBuild = "" // Esto sólo se usa en proyectos antiguos que tienen el entorno dentro del POM
def unique_release = false
def num_rama_release = ""
def num_rama_release_local = ""
def num_rama_release_version = ""
String [] NomDespliegue = new String[4] // Tamaño total
String [] RutaDespliegue = new String[4] // Tamaño total
def CleanWorkspace = ""
String SonarCoverageResponse = "" // Para aviso de cobertura de código en email cuando se pasa a PRE
def proyKey = "" // Se pone aquí para usarlo en verificación de cobertura de código cuando se pasa a PRE
def userID = "" // Se incluye el Usuario que lanza el proceso para validaciones Sonar y logs
def branchAux = ""

def call(Map params) {
    pipeline {
        agent any
        
        options {
            timeout(time: 1, unit: 'HOURS')   // timeout on whole pipeline job
        }
    
        tools{
	         maven "apache-maven-3.8.6"
	         jdk "jdk1.8.0_321"
	   }

        
        environment{
            env_desa = 'Desarrollo'
            env_pre  = 'Pre-produccion'
            env_pro  = 'Produccion'
            env_val  = 'Validacion'
            env_hotf = 'Hotfix'
        }
        stages {
            stage('Entrada Version') {
                steps {
                  timestamps {
                        logstash {
                            script{
                                def sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
                                echo "Fecha de inicio lanzamiento: " + sdf.format(new Date())

                                try{
                                    userID = currentBuild.getRawBuild().getCauses()[0].getUserId()
                                }catch (e){
                                    userID = "ERROR"
                                    println ("Error al recuperar el usuario que lanza el JOB")
                                }  

                                

                                branchAux = env.BRANCH_NAME    
                                if (env.BRANCH_NAME.matches("HOTFIX(.*)")) 
                                    branchAux = "HOTFIX"
                                if (env.BRANCH_NAME.matches("RELEASE(.*)"))
                                    branchAux = "RELEASE"
                                if (env.BRANCH_NAME.matches("feature(.*)"))
                                    branchAux = "feature"
                                
                                echo "branchAux: ${branchAux}" 
                                params.Tecnologia="functions_" + params.Tecnologia;
                                NomDespliegue = params.get("NomDespliegue");
                                RutaDespliegue = params.get("RutaDespliegue");
                                CleanWorkspace = params.get("CleanWorkspace");
                                SmokeTest = params.get("SmokeTest");
                                
                                if(env.BRANCH_NAME.matches("master")){
                                    // Si no hay versión de entrada, indica que la tarea ha sido lanzada automáticamente para indexar la rama.
                                    echo "La Rama ha sido Indexada correctamente."
                                    currentBuild.result = 'NOT_BUILT'
                                    return
                                }

                                env.ENTORNO=""
                                jobBaseName = "${env.JOB_NAME}".split('/')[0]
                                try {
                                    timeout(time: 30, unit: 'SECONDS') { 
                                        
                                        if((!env.BRANCH_NAME.matches("HOTFIX(.*)")) && (!env.BRANCH_NAME.matches("RELEASE(.*)"))){
                                            def INPUT_PARAMS = input(
                                            id: 'env.VERSION', 
                                            message: 'Versión a desplegar', 
                                            parameters: [
                                            [$class: 'StringParameterDefinition', defaultValue: '0', description: 'Version Major', name: 'MAJOR'],
                                            [$class: 'StringParameterDefinition', defaultValue: '0', description: 'Version Minor', name: 'MINOR'],
                                            [$class: 'StringParameterDefinition', defaultValue: '0', description: 'Version parcheo de bugfix', name: 'PATCH']
                                            ]);
                                            
                                            MAJOR = INPUT_PARAMS.MAJOR;
                                            MINOR = INPUT_PARAMS.MINOR;
                                            PATCH = INPUT_PARAMS.PATCH;
                                        } else {
                                        
                                            VERSION_RAMA =  "${env.BRANCH_NAME}".split('-')[1].tokenize(".")
                                                MAJOR = VERSION_RAMA[0].toString()
                                                MINOR = VERSION_RAMA[1].toString()
                                                PATCH = VERSION_RAMA[2].toString()
                                        }

                                    }
                                } catch(err) { // timeout reached or input false
                                    user = err.getCauses()[0].getUser().toString()
                                    if (user.toString() == 'SYSTEM') {  // Si el usuario es SYSTEM, es un timeout
                                    echo "Job abortado por TIMEOUT en la selección de versión"
                                    } else {  // if not and input is false it's the user
                                    echo "Job cancelado por usuario: [${user}]"
                                    }
                                    currentBuild.result = 'ABORTED'
                                    throw new hudson.AbortException('')
                                }
                                echo ("Versión: "+ MAJOR + "." + MINOR + "." + PATCH)

                            }
                        }
                    }
                }
            }
            stage ('Número Versión') {
                steps {
                    timestamps {
                        logstash {
                            script {
                                if((env.BRANCH_NAME.matches("HOTFIX(.*)")) || (env.BRANCH_NAME.matches("RELEASE(.*)")) || (env.BRANCH_NAME.matches("develop")) || (env.BRANCH_NAME.matches("feature(.*)"))){
                                    try {
                                        if (MAJOR && ("0.0.0" != VersionNumber([versionNumberString: "${MAJOR}.${MINOR}.${PATCH}"]))){
                                            VERSION_CONTROL = MAJOR + "." + MINOR + "." + PATCH;
                                            VERSION_INPUT = VersionNumber([versionNumberString: "${MAJOR}.${MINOR}.${PATCH}"])                                    
                                            VERSION = VERSION_CONTROL +'.0';
                                            echo "Version obtenida por input: ${VERSION}"
                                        }else{
                                            throw err
                                        }
                                    }catch(err){
                                            echo "La Rama ha sido Indexada correctamente."
                                            env.BRANCH_NAME = 'master';        // Evitamos que entre en ninguna otra tarea.
                                            currentBuild.result = 'NOT_BUILT'
                                            return
                                    }
                                    
                                    // Comprobamos si existe una rama Release creada, y si existe, es la misma versión que la actual
                                    echo "before:unique_rama_release:${VERSION}"//,${num_rama_release},${num_rama_release_version}" 

                                    sshagent(['jenkins120_GitLab']) {
                                        sh "git fetch -p origin"
                                    }

                                    def release_version_line = "(git branch -a | grep -i origin/RELEASE-"+VERSION+" | wc -l)"
                                    
                                    // número total ramas de release
                                    num_rama_release = sh (script: "(git branch -a | grep -i origin/release | wc -l)", returnStdout: true).trim()
                                    // número de ramas de versión actual
                                    num_rama_release_version = sh (script: release_version_line, returnStdout: true).trim()
                                    
                                    unique_release = (num_rama_release == '0' || ((num_rama_release_version == '1') && (num_rama_release == '1'))).toBoolean()
                                    
                                    echo "num_rama_release: ${num_rama_release}; num_rama_release_version: ${num_rama_release_version}; unique_release = ${unique_release}"



                                    
                                    if (env.BRANCH_NAME == 'develop' || (env.BRANCH_NAME.matches("feature(.*)"))) {            // Si ejecutamos desde la rama Develop
                                        // Si ya existen Ramas en Release, recogemos su versión
                                        if(!unique_release){
                                            VERSION_REMOTA = functions_commons.remote_branch_version().tokenize(".")
                                                MAJOR = VERSION_REMOTA[0].toString()
                                                MINOR = VERSION_REMOTA[1].toString()
                                                PATCH = VERSION_REMOTA[2].toString()
                                            VERSION_CONTROL = VersionNumber([versionNumberString: "${MAJOR}.${MINOR}.${PATCH}"])
                                    
                                        }
                                    
                                        echo 'Rama DEVELOP. Versión de proyecto: ' + VERSION
                                        env.ENTORNO = "${env_desa}"
                                    
                                    }else{        // Si ejecutamos desde la rama Release o Hotfix
                                    
                                        if (env.BRANCH_NAME.matches("HOTFIX(.*)")){
                                            
                                            echo 'Rama HOTFIX. Versión de proyecto: ' + VERSION
                                            env.ENTORNO = "${env_hotf}"
                                        }
                                            
                                        if (env.BRANCH_NAME.matches("RELEASE(.*)")){
                                            
                                            // Recogemos la versión del proyecto a partir del nombre de la rama
                                            VERSION_RAMA_ACTUAL = "${env.BRANCH_NAME}".split('-')[1].tokenize(".")
                                                MAJOR = VERSION_RAMA_ACTUAL[0].toString()
                                                MINOR = VERSION_RAMA_ACTUAL[1].toString()
                                                PATCH = VERSION_RAMA_ACTUAL[2].toString()
                                                BUILD = VERSION_RAMA_ACTUAL[3].toInteger() + 1
                                            VERSION_CONTROL = VersionNumber([versionNumberString: "${MAJOR}.${MINOR}.${PATCH}"])
                                            VERSION = VersionNumber([versionNumberString: "${MAJOR}.${MINOR}.${PATCH}.${BUILD}"])
                                            
                                            echo 'Rama RELEASE. Versión de proyecto: ' + VERSION
                                            env.ENTORNO = "${env_pro}"
                                        }
                                    }
                                
                                } else {
                                    echo "Esta rama no debe ejecutarse en Jenkins"
                                    currentBuild.result = 'NOT_BUILT'
                                    return;
                                }
                            }
                        }
                    }
                }  
            }

            stage('Build & SonarQube Test') {
                when {
                  expression { 
                       (
                        ((env.BRANCH_NAME.matches("HOTFIX(.*)")) || 
                        (env.BRANCH_NAME.matches("RELEASE(.*)")) ||  
                        (env.BRANCH_NAME.matches("feature(.*)")) ||
                        (env.BRANCH_NAME.matches("develop")))  &&
                        (!env.ENTORNO.matches("") && !env.ENTORNO.matches("Produccion"))
                       )  
                    }                    
                }
                steps {
                    timestamps {
                        logstash {
                            script{
                                print env.ENTORNO;
                                // Control por función nueva if (env.BRANCH_NAME.matches("feature(.*)"))
                                //    params.LanzarSonar = "0"
                                    
                                if(env.ENTORNO == 'Desarrollo' || env.ENTORNO == ''){
                                    PerfilPomBuild = params.PPomDesarrollo
                                } else {
                                    if(env.ENTORNO == 'Validacion'){
                                        PerfilPomBuild = params.PPomValidacion
                                    }else{
                                        PerfilPomBuild = params.PPomPreproduccion
                                    }
                                }
                                if(PerfilPomBuild != "" && PerfilPomBuild != null)
                                    params.ParametrosMaven = params.ParametrosMaven + " " + PerfilPomBuild
                                // <- Esto es para proyectos antiguos que tienen el entorno dentro del POM
                                def project = readMavenPom file: "${params.RutaPom}"
                                def proyName=project.name
                                if (project.name==null)
                                    proyName=project.artifactId
                                if (project.artifactId==null)
                                    proyName=jobBaseName
                                SonarCoverageResponse="Llamada Validación Sonar";
                                proyKey=project.groupId+':'+project.artifactId
                                if (project.groupId==null)
                                    proyKey=project.parent.groupId+':'+project.artifactId 
                                // Consulta excepciones de Lanzar Sonar para el proyecto:
                                def l_fileVar = "/var/lib/jenkins/Jenkins_core_pipeline";
                                def proyKeyAux = proyKey.replace("%3A",":")
                                if (params.LanzarSonar=="1"){
                                  res = functions_commons.getVarFile(l_fileVar, proyKeyAux, branchAux, env.ENTORNO, userID, "LanzarSonar", "Integer");
                                  println ("Llamada recuperar excepción Lanzar Sonar para proyecto: ${proyKey}, rama: ${branchAux}, entorno: ${env.ENTORNO}, Usuario: ${userID}. LanzarSonar antes: ${params.LanzarSonar} Recupera: ${res}");                         
                                    params.LanzarSonar = res.toString() 
                                }
                                 println ("Build del Proyecto")
			def output = sh(script: "mvn -f ${params.RutaPom} clean install ${params.ParametrosMaven} ", returnStdout: true)
            echo "Salida de mvn" + output
            def artifact = findFiles glob: '**/P*.jar'
            if (artifact) {
                    echo "Salida definitiva....."
                    for (int j = 0; j < artifact.length; j++) {
                            echo "---> ${artifact[j].name}" 
							//if ("${artifact[i].name}.ok".equals("${scriptsOk[j].name}")) {

                           // }
                    }
            }
                            }
                        }
                    }
                }
            }
            stage('Smoke Test') {
                                when {
                                    expression {(params.SmokeTest=='1')}
                                }
                                    steps {
                                        timestamps {
                                        logstash {
                                        script{
                                            try {
                                                sleep 60
                                

                                 echo "******* pruebas funcionales Smoke Test*******"
        
         def rutaPomAux = params.RutaPom 
         def conecta_class =""
         if (rutaPomAux != "pom.xml"){
            echo "******* ${JOB_BASE_NAME}****  ${HOME}  *** ${env.WORKSPACE}"
            String[] folder_hasta_Pom = rutaPomAux.split('/')
            
            for(i=0; i < folder_hasta_Pom.length - 1;i++){
                   conecta_class = conecta_class + folder_hasta_Pom[i] + "/"
            }
            echo "******* ${JOB_BASE_NAME}****  ${HOME}  *** ${env.WORKSPACE} ****** ${folder_hasta_Pom.length} --- ${conecta_class}"
            //String DestinoClasses = rutaPomAux.split('/',folder_hasta_Pom.length - 1)
         }
sh "java -cp '${conecta_class}target/dependency/testng-7.4.0.jar:${conecta_class}target/dependency/jcommander-1.81.jar:${conecta_class}target/dependency/jquery-3.5.1.jar:${env.WORKSPACE}/${conecta_class}${params.clases_compile}' org.testng.TestNG ${env.WORKSPACE}/testng.xml"
                                            } catch(err) { // timeout reached or input false
                                                echo "Ha ocurrido un error al  realizar las pruebas funcionales Smoke Test" + err
                                            
                                            }
                                        }
                                    }
                                    }}
                            }
      }
}
}
return this
