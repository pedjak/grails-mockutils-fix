class GrailsMockutilsFixGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.7"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/**",
		"webapp/**"
    ]

    def author = "Predrag Knezevic"
    def authorEmail = "pedjak@gmail.com"
    def title = "Fix for GRAILS-7309"
    def description = '''\\
Fixing issue http://jira.grails.org/browse/GRAILS-7309 by patching MockUtils class
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/grails-mockutils-fix"

}
