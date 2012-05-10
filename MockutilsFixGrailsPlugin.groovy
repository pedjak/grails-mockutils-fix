class MockutilsFixGrailsPlugin {
    // the plugin version matches Grails version
    def version = "1.3.7-2"
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
    def documentation = "http://github.com/pedjak/grails-mockutils-fix"

}
