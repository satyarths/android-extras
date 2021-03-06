#!/usr/bin/env groovy
/**
 * based on  dhelberg's script.
 * Improve command line parsing
 */


gfx_command_map = ['on' : 'visual_bars', 'off' : 'false', 'lines' : 'visual_lines']
layout_command_map = ['on' : 'true', 'off' : 'false']
overdraw_command_map = ['on' : 'show',  'off' : 'false', 'deut' : 'show_deuteranomaly']
overdraw_command_map_preKitKat = ['on' : 'true',  'off' : 'false']
show_updates_map = ['on' : '0',  'off' : '1']
battery_map=['on':'1','off':'0']
deeplink_map=['enter the deeplink for the $app_name added':0]
text_map=['enter the text to input':0]
clear_map=['clear app data ,provide app package name':0]
launch_map=['launch app by providing package name':0]
wifi_connect_map=['ipconn add ip address to connect':0]
screenshot_map=['filename for screenshot ,path is on desktop':0]
debug_launch_map=['app_package':0]
app_name="com.gojek.app.dev"


command_map = ['gfx' : gfx_command_map,
               'layout' : layout_command_map,
               'overdraw' : overdraw_command_map,
               'updates' : show_updates_map,
               'battery': battery_map,
               'deeplink':deeplink_map,
               'text':text_map,
               'clear':clear_map,
               'launch':launch_map,
               'ipconn':wifi_connect_map,
               'debuglaunch':debug_launch_map,
               'screenshot':screenshot_map]


verbose = false

def cli = new CliBuilder(usage:'devtools.groovy command option')
cli.with {
    v longOpt: 'verbose', 'prints additional output'
}
def opts = cli.parse(args)
if(!opts)
    printHelp("not provided correct option")
if(opts.arguments().size() != 2)
    printHelp("you need to provide two arguments: command and option")
if(opts.v)
    verbose = true

//get args
String command = opts.arguments().get(0)
String option = opts.arguments().get(1)

//get adb exec
adbExec = getAdbPath();

//check connected devices
def adbDevicesCmd = "$adbExec devices"
def proc = adbDevicesCmd.execute()
proc.waitFor()

def foundDevice = false
deviceIds = []

proc.in.text.eachLine { //start at line 1 and check for a connected device
        line, number ->
            if(number > 0 && line.contains("device")) {
                foundDevice = true
                //grep out device ids
                def values = line.split('\\t')
                if(verbose)
                    println("found id: "+values[0])
                deviceIds.add(values[0])
            }
}

if(!foundDevice) {
    println("No usb devices")
    System.exit(-1)
}


def adbcmd = ""
switch ( command ) {
    case "gfx" :
        adbcmd = "shell setprop debug.hwui.profile "+gfx_command_map[option]
        executeADBCommand(adbcmd)
        break
    case "layout" :
        adbcmd = "shell setprop debug.layout "+layout_command_map[option]
        executeADBCommand(adbcmd)
        break
    case "overdraw" :
        //tricky, properties have changed over time
        adbcmd = "shell setprop debug.hwui.overdraw "+overdraw_command_map[option]
        executeADBCommand(adbcmd)
        adbcmd = "shell setprop debug.hwui.show_overdraw "+overdraw_command_map_preKitKat[option]
        executeADBCommand(adbcmd)
        break
    case "updates":
        adbcmd = "shell service call SurfaceFlinger 1002 android.ui.ISurfaceComposer"+show_updates_map[option]
        executeADBCommand(adbcmd)
        break
    case "battery":
        adbcmd = "shell su -c ' echo "+battery_map[option]+" > /sys/devices/qpnp-charger-ee236a00/power_supply/battery/charging_enabled'"
        executeADBCommand(adbcmd)
        break
    case "deeplink":
        adbcmd="shell am start -a android.intent.action.VIEW -d "+ option +" "+app_name
        executeADBCommand(adbcmd)
        break
    case "text":
        adbcmd="shell input text "+option
        executeADBCommand(adbcmd)
        break
    case "clear":
        adbcmd="shell pm clear "+option
        executeADBCommand(adbcmd)
        break
    case "launch":
        adbcmd="shell monkey -p "+option+"  -c android.intent.category.LAUNCHER 1"
        executeADBCommand(adbcmd)
        break
    case "ipconn":
      adbcmd="connect "+option
      executeADBCommand(adbcmd)
      break
    case "screenshot":
      adbcmd="shell screencap -p  > ~/Desktop/"+option+".png"
      executeADBCommand(adbcmd)
      break
    case "debuglaunch":
      adbcmd="shell am set-debug-app -w "+option
      executeADBCommand(adbcmd)
      break
    default:
        printHelp("could not find the command $command you provided")
}



kickSystemService()

System.exit(0)






void kickSystemService() {
    def proc
    int SYSPROPS_TRANSACTION = 1599295570 // ('_'<<24)|('S'<<16)|('P'<<8)|'R'

    def pingService = "shell service call activity $SYSPROPS_TRANSACTION"
    executeADBCommand(pingService)
}

void executeADBCommand(String adbcmd) {
    if(deviceIds.size == 0) {
        println("no devices connected")
        System.exit(-1)
    }
    deviceIds.each { deviceId ->
        def proc
        def adbConnect = "$adbExec -s $deviceId $adbcmd"
        if(verbose)
            println("Executing $adbConnect")
        proc = adbConnect.execute()
        proc.waitFor()
        println(proc.text)
    }
}
void executeInShellCommand(String command){
 if(deviceIds.size == 0) {
        println("no devices connected")
        System.exit(-1)
    }
    deviceIds.each { deviceId ->
        def proc

        def adbConnect = "$cmd"
        if(verbose)
            println("Executing $adbConnect")
        proc = adbConnect.execute()
        proc.waitFor()
        }

}


String getAdbPath() {
    def adbExec = "adb"
    if(isWindows())
        adbExec = adbExec+".exe"
    try {
        def command = "$adbExec"    //try it plain from the path
        command.execute()
        if(verbose)
            println("using adb in "+adbExec)
        return adbExec
    }
    catch (IOException e) {
        //next try with Android Home
        def env = System.getenv("ANDROID_HOME")
        if(verbose)
            println("adb not in path trying Android home")
        if (env != null && env.length() > 0) {
            //try it here
            try {
                adbExec = env + File.separator + "platform-tools" + File.separator + "adb"
                if(isWindows())
                    adbExec = adbExec+".exe"

                def command = "$adbExec"// is actually a string
                command.execute()
                if(verbose)
                    println("using adb in "+adbExec)

                return adbExec
            }
            catch (IOException ex) {
                println("Could not find $adbExec in path and no ANDROID_HOME is set :(")
                System.exit(-1)
            }
        }
        println("Could not find $adbExec in path and no ANDROID_HOME is set :(")
        System.exit(-1)
    }
}

boolean isWindows() {
    return (System.properties['os.name'].toLowerCase().contains('windows'))
}

void printHelp(String additionalmessage) {
    println("usage: devtools.groovy [-v] command option")
    print("command: ")
    command_map.each { command, options ->
        print("\n  $command -> ")
        options.each {
            option, internal_cmd -> print("$option ")
        }
    }
    println()
    println("Error $additionalmessage")
    println()

    System.exit(-1)
}
