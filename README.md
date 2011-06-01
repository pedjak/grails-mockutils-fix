This plugin fixes http://jira.grails.org/browse/GRAILS-7309 issue by patching
MockUtils class at runtime, before executing tests.

The required Grails version is 1.3.7

The plugin is not published in the official Grails repository. Hence,
add following fragment to your BuildConfig.groovy:

    repositories {
        def libResolver = new org.apache.ivy.plugins.resolver.URLResolver()
        libResolver.addArtifactPattern("http://cloud.github.com/downloads/pedjak/grails-mockutils-fix/grails-[artifact]-[revision].[ext]")
        libResolver.name = "mockutils-fix-repo"
        libResolver.settings = ivySettings
        
        resolver libResolver
     }
     
     plugins {
        test 'org.grails.plugins:mockutils-fix:1.3.7'
     }