/**
 *  MiHome Monitor
 *
 *  Copyright 2016 Alex Lee Yuk Cheung
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *	VERSION HISTORY
 *	12.12.2017: 2.0.1 - Resolve Android chart display issue.
 *  23.11.2016:	2.0 - Remove extra logging.
 *
 *  10.11.2016: 2.0 BETA Release 2.2 - Stop potential executeAction() errors.
 *  10.11.2016: 2.0 BETA Release 2.1 - Shift from data to state to hold variables.
 *	09.11.2016:	2.0 BETA Release 2 - Try to fix chart Android compatibility.
 *	09.11.2016:	2.0 BETA Release 1 - Support for MiHome (Connect) v2.0. Inital version of device.
 */
metadata {
	definition (name: "MiHome Monitor", namespace: "alyc100", author: "Alex Lee Yuk Cheung") {
		capability "Polling"
		capability "Power Meter"
		capability "Refresh"
		capability "Sensor"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"power", type:"generic", width:6, height:4, canChangeIcon: true) {
    		tileAttribute("device.power", key: "PRIMARY_CONTROL") {
      			attributeState "default", label: '${currentValue} W', icon:"st.Appliances.appliances17", backgroundColor:"#69b62c"
    		}
  		}
        
        valueTile("totalPower", "device.totalPower", decoration: "flat", width: 3, height: 1) {
			state "default", label: 'Today Total Power:\n${currentValue} Wh'
		}
        
        valueTile("yesterdayTotalPower", "device.yesterdayTotalPower", decoration: "flat", width: 3, height: 1) {
			state "default", label: 'Yesterday Total Power:\n${currentValue} Wh'
		}
        
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
        htmlTile(name:"chartHTML", action: "getChartHTML", width: 6, height: 4, whiteList: ["www.gstatic.com", "raw.githubusercontent.com"])
        
        main (["power"])
		details(["power", "totalPower", "yesterdayTotalPower", "chartHTML", "refresh"])
	}
}

mappings {
	path("/getChartHTML") {action: [GET: "getChartHTML"]}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
	// TODO: handle 'power' attribute

}

// handle commands
def poll() {
	log.debug "Executing 'poll' for ${device} ${this} ${device.deviceNetworkId}"
    
    def resp = parent.apiGET("/subdevices/show?params=" + URLEncoder.encode(new groovy.json.JsonBuilder([id: device.deviceNetworkId.toInteger()]).toString()))
	if (resp.status != 200) {
		log.error("Unexpected result in poll(): [${resp.status}] ${resp.data}")
		return []
	}
    
    def real_power = resp.data.data.real_power
    //def real_power = Math.abs(new Random().nextInt() % 600 + 1)
    if (real_power != null) {
   		sendEvent(name: "power", value: real_power as BigDecimal, unit: "Wh")
    }
    
    def today_wh = resp.data.data.today_wh
    // def today_wh = Math.abs(new Random().nextInt() % 3000 + 1)
    if (today_wh != null) {
    
    	//Calculate change of day
    	def df = new java.text.SimpleDateFormat("D")
        if (location.timeZone) {
			df.setTimeZone(location.timeZone)
		}
		else {
			df.setTimeZone(TimeZone.getTimeZone("Europe/London"))
		}
        def currentDay = df.format(new Date()).toInteger()
        def changeOfDay = false
        if ((state.day != null) && (currentDay != state.day)) {
        	changeOfDay = true
        }
        state.day = currentDay
    	
        if (state.last_wh_reading != null) {
        	//Determine when the days total wh reading has been reset and store as chart data.
        	if (((today_wh as BigDecimal) < state.last_wh_reading) || (today_wh == 0 && changeOfDay)) { 
            	addYesterdayTotalToChartData(state.last_wh_reading)
                sendEvent(name: "yesterdayTotalPower", value: state.last_wh_reading, unit: "Wh")
            }
        }
    	state.last_wh_reading = today_wh as BigDecimal
    	sendEvent(name: "totalPower", value: today_wh as BigDecimal, unit: "Wh")
        addCurrentTotalToChartData(today_wh as BigDecimal)
    }
}

def refresh() {
	log.debug "Executing 'refresh'"
	poll()
}

def addYesterdayTotalToChartData(total) {
	if (state.chartData == null) {
    	state.chartData = [0, total, 0, 0, 0, 0, 0]
    }
    else {
    	state.chartData.putAt(0, total)
    	state.chartData.add(0, 0)
        state.chartData.pop()
    }
}

def addCurrentTotalToChartData(total) {
	if (state.chartData == null) {
    	state.chartData = [total, 0, 0, 0, 0, 0, 0]
    }
    state.chartData.putAt(0, total)
}

def getChartHTML() {
	try {
    	def date = new Date()
		if (state.chartData == null) {
    		state.chartData = [0, 0, 0, 0, 0, 0, 0]
    	}
        def topValue = state.chartData.max()
        def hData = ""
        if (state.last_wh_reading != null && state.last_wh_reading > 0) {
			hData = """
            <h4 style="font-size: 22px; font-weight: bold; text-align: center; background: #00a1db; color: #f5f5f5;">Historical Usage</h4><br>
	  		<div id="main_graph" style="width: 100%; height: 260px;"><img src="http://chart.googleapis.com/chart?cht=bvg&chs=350x200&chxt=x,y,y&chco=0a9928|0a9928|0a9928|0a9928|0a9928|0a9928|eda610&chd=t:${state.chartData.getAt(6)},${state.chartData.getAt(5)},${state.chartData.getAt(4)},${state.chartData.getAt(3)},${state.chartData.getAt(2)},${state.chartData.getAt(1)},${state.chartData.getAt(0)}&chds=0,${topValue + 2}&chxl=0:|${(date - 6).format("d MMM")}|${(date - 5).format("d MMM")}|${(date - 4).format("d MMM")}|${(date - 3).format("d MMM")}|${(date - 2).format("d MMM")}|${(date - 1).format("d MMM")}|${date.format("d MMM")}|2:|Power(Wh)&chxp=2,50&chxr=1,0,${topValue+2}&chbh=a,10,10"></div>		  
			"""
        } else {
        	hData = """
            	<div class="centerText" style="font-family: helvetica, arial, sans-serif;">
				  <p>Not enough data to create power history chart.</p>
				  <p>Currently collecting data. Come back in a couple of hours.</p>
				</div>
            """
        }

		def mainHtml = """
		<!DOCTYPE html>
		<html>
			<head>
				<head>
				<meta http-equiv="cache-control" content="max-age=0"/>
				<meta http-equiv="cache-control" content="no-cache"/>
				<meta http-equiv="expires" content="0"/>
				<meta http-equiv="expires" content="Tue, 01 Jan 1980 1:00:00 GMT"/>
				<meta http-equiv="pragma" content="no-cache"/>
				<meta name="viewport" content="width = device-width, user-scalable=no, initial-scale=1.0">

				<link rel="stylesheet prefetch" href="${getCssData()}"/>
			</head>
			</head>
			<body>
                ${hData}
			</body>
			</html>
		"""
		render contentType: "text/html", data: mainHtml, status: 200
	}
	catch (ex) {
		log.error "getChartHTML Exception:", ex
	}
}

def getCssData() {
	def cssData = null
	def htmlInfo
	state.cssData = null

	if(htmlInfo?.cssUrl && htmlInfo?.cssVer) {
		if(state?.cssData) {
			if (state?.cssVer?.toInteger() == htmlInfo?.cssVer?.toInteger()) {
				//LogAction("getCssData: CSS Data is Current | Loading Data from State...")
				cssData = state?.cssData
			} else if (state?.cssVer?.toInteger() < htmlInfo?.cssVer?.toInteger()) {
				//LogAction("getCssData: CSS Data is Outdated | Loading Data from Source...")
				cssData = getFileBase64(htmlInfo.cssUrl, "text", "css")
				state.cssData = cssData
				state?.cssVer = htmlInfo?.cssVer
			}
		} else {
			//LogAction("getCssData: CSS Data is Missing | Loading Data from Source...")
			cssData = getFileBase64(htmlInfo.cssUrl, "text", "css")
			state?.cssData = cssData
			state?.cssVer = htmlInfo?.cssVer
		}
	} else {
		//LogAction("getCssData: No Stored CSS Info Data Found for Device... Loading for Static URL...")
		cssData = getFileBase64(cssUrl(), "text", "css")
	}
	return cssData
}

def getFileBase64(url, preType, fileType) {
	try {
		def params = [
			uri: url,
			contentType: '$preType/$fileType'
		]
		httpGet(params) { resp ->
			if(resp.data) {
				def respData = resp?.data
				ByteArrayOutputStream bos = new ByteArrayOutputStream()
				int len
				int size = 4096
				byte[] buf = new byte[size]
				while ((len = respData.read(buf, 0, size)) != -1)
					bos.write(buf, 0, len)
				buf = bos.toByteArray()
				//LogAction("buf: $buf")
				String s = buf?.encodeBase64()
				//LogAction("resp: ${s}")
				return s ? "data:${preType}/${fileType};base64,${s.toString()}" : null
			}
		}
	}
	catch (ex) {
		log.error "getFileBase64 Exception:", ex
	}
}

def cssUrl()	 { return "https://raw.githubusercontent.com/desertblade/ST-HTMLTile-Framework/master/css/smartthings.css" }