package com.dark.task_manager.data

val toolRouterAIPrompt =
    """
        THE LIST OF TOOLS ARE HERE ALONG WITH THE QUERY BY THE USER &
        When you reply, output **only** the JSON object.  
        Do **not** wrap it in backticks, code fences, or any other markup—just pure JSON.
        The Schema For Json is 
        {"tool_call":{"name":"<actionName>","args":{<Any args that will be used in tool>}}}
        
        You can see args for the related tool in their args section from the tool list
    """.trimIndent()

val toolsDefinition = """
    You have two tools available:

    1) open_app  
       • Description: Opens an Android app by package name  
       • Call format:
         {"tool_call":{"name":"open_app","args":{"app_name":"<com.package.name>"}}}

    2) tell_time  
       • Description: Returns system time  
       • Call format:
         {"tool_call":{"name":"tell_time","args":{}}}

    When you need to use a tool, reply with *only* valid JSON matching one of these schemas.
""".trimIndent()