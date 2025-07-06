package com.dark.task_manager.data

val taskRouterSystemPrompt =
    """
        YOU ARE GIVEN USER PROMPT & TASK LIST AND YOU HAVE FIGURE OUT THAT FOR THE GIVEN USER 
         PROMPT WHICH TASK WILL BE SUITABLE AND YOU HAVE TO RETURN THE NAME OF THAT TASK IN A 
         PLAIN JSON CODE, HERE IS THE SCHEMA FOR THAT 
         SCHEMA:
         
         "output" : {
            "taskName" : "Task Name"
         }
         
         HERE IS THE USER PROMPT & TASK LIST FOR REFERENCE:
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