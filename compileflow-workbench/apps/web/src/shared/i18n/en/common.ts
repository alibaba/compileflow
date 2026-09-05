const enCommon = {
  'common.loading': 'Loading…',
  'common.skipToContent': 'Skip to main content',

  'common.retry': 'Retry',

  'common.back': 'Back',

  'common.next': 'Next',

  'common.prev': 'Previous',

  'common.previousPage': 'Previous page',

  'common.nextPage': 'Next page',

  'common.confirm': 'Confirm',

  'common.cancel': 'Cancel',

  'common.save': 'Save',

  'common.delete': 'Delete',

  'common.edit': 'Edit',

  'common.view': 'View',

  'common.refresh': 'Refresh',

  'common.loadMore': 'Load more',

  'common.search': 'Search',

  'common.filter': 'Filter',

  'filters.processType': 'Process Type',

  'common.clear': 'Clear',

  'common.total': 'Total',

  'common.examples': 'examples',

  'common.getStarted': 'Get started',

  'common.open': 'Open',

  'common.close': 'Close',

  'common.breadcrumb': 'Breadcrumb',

  'nav.home': 'Home',

  'nav.learn': 'Learn',

  'nav.learn.examples': 'Examples',

  'nav.learn.tutorials': 'Tutorials',

  'nav.workspace': 'Build',

  'nav.workspace.designer': 'Designer',

  'nav.workspace.editor': 'Editor',

  'nav.workspace.debugger': 'Debugger',

  'nav.ops': 'Operate',

  'nav.build': 'Build',

  'nav.operate': 'Operate',

  'nav.ops.processes': 'Processes',

  'nav.ops.deployment': 'Deployments',

  'nav.ops.monitoring': 'Monitoring',

  'nav.ops.logs': 'Logs',

  'nav.learn.exampleDetail': 'Example',

  'nav.ops.deploymentDetail': 'Deployment',

  // Page titles (appended with " | CompileFlow Workbench")

  'pageTitle.home': 'Learn',

  'pageTitle.learn': 'Learn Process Patterns',

  'pageTitle.build': 'Design & Compile',

  'pageTitle.build.workspace': 'Design & Compile',

  'pageTitle.operate': 'Deploy & Operate',

  'pageTitle.operate.processes': 'Processes',

  'pageTitle.operate.deployments': 'Deployments',

  'pageTitle.operate.monitoring': 'Runtime Monitoring',

  'pageTitle.operate.logs': 'Execution Logs',

  'pageTitle.settings': 'Settings',

  'pageTitle.learn.detail': 'Example details',

  'pageTitle.operate.deployWizard': 'New deployment',

  'pageTitle.operate.deploymentDetail': 'Deployment details',

  'pageTitle.notFound': 'Page not found',

  'pageTitle.serverError': 'Server error',

  // Application shell

  'navigation.main': 'Main navigation',

  'navigation.mainMenu': 'Main menu',

  'navigation.home': 'Go home',

  'navigation.github': 'GitHub',

  'navigation.toggleToEnglish': 'Switch to English',

  'navigation.toggleToChinese': 'Switch to Chinese',

  'navigation.openMenu': 'Open menu',

  'navigation.drawerTitle': 'CompileFlow Workbench',

  'navigation.expandSidebar': 'Expand sidebar',

  'navigation.collapseSidebar': 'Collapse sidebar',

  'navigation.openSidebar': 'Open section navigation',

  'navigation.closeSidebar': 'Close section navigation',

  'sidebar.exampleCategories': 'Categories',

  'sidebar.category.basics': 'Getting started',

  'sidebar.category.business': 'Business scenarios',

  'sidebar.category.advanced': 'Advanced',

  'sidebar.tutorials': 'Tutorials',

  // Application menu and settings

  'appMenu.label': 'App menu',

  'appMenu.settings': 'Settings',

  'appMenu.documentation': 'Docs',

  'appMenu.language': 'Language',

  'settings.title': 'Settings',

  'settings.subtitle': 'Preferences and build details',

  'settings.eyebrow': 'Preferences',

  'settings.preferences': 'Preferences',

  'settings.language': 'Language',

  'settings.theme': 'Theme',

  'settings.theme.light': 'Light',

  'settings.theme.dark': 'Dark',

  'settings.build': 'Build info',

  'settings.operateMode': 'Operate mode',

  'settings.buildMode': 'Build mode',

  'settings.appVersion': 'App version',

  'settings.resources': 'Resources',

  // Home Page

  'home.eyebrow': 'AI-ready process orchestration for Java',

  'home.title': 'Build the reliable process layer for the AI era',

  'home.subtitle':
    'Design, compile, publish, and operate high-performance Java processes—from deterministic business logic to durable execution and agent-ready orchestration.',

  'home.start': 'Build your first process',

  'home.docs': 'Explore examples',

  'home.status': 'Status',

  'home.modules': 'Modules',

  'home.version': 'v{{version}} - {{status}}',

  'home.features': 'From idea to reliable execution',

  'home.featuresDesc':
    'Learn proven patterns, build visually, and operate every version with confidence.',

  // Features

  'feature.learn.title': 'Learn',

  'feature.learn.desc':
    'Explore real process patterns—from deterministic decisions to durable waits and governed effects.',

  'feature.learn.action': 'Explore examples',

  'feature.workspace.title': 'Build',

  'feature.workspace.desc':
    'Design visually, edit source, validate, and compile Java processes in one workspace.',

  'feature.workspace.action': 'Open workspace',

  'feature.ops.title': 'Operate',

  'feature.ops.desc':
    'Publish immutable versions, roll out safely, and trace every execution to its exact source.',

  'feature.ops.action': 'Open operations',

  // Example List

  'theme.light': 'Light mode',

  'theme.dark': 'Dark mode',

  // Process Types

  'error.loadFailed': 'Could not load',

  'error.exampleNotFound': 'Example not found',

  'error.networkError': 'Network error—try again.',

  'error.deleteFailed': 'Could not delete',

  'error.duplicateFailed': 'Could not duplicate',

  'error.deployFailed': 'Could not deploy',

  'error.rollbackFailed': 'Could not roll back',

  'error.abortFailed': 'Could not abort',

  'error.requestFailed': 'Request failed',

  'error.requestTimeout': 'Request timed out—try again later.',

  'error.networkConnection': 'Network error—check your connection and try again.',

  'error.unknown': 'Something went wrong',

  'error.operationFailed': 'Operation failed',

  'error.pageRenderTitle': 'This page hit an error',

  'error.pageRenderDescription': 'Refresh the page and try again.',

  'error.application': 'The app hit an error. Refresh and try again.',

  'error.retryExhausted': 'Still failing after retries',

  'errorBoundary.title': 'Something went wrong',

  'errorBoundary.description': 'Try again, or go back home.',

  'errorBoundary.retry': 'Try again',

  'errorBoundary.home': 'Go home',

  'errorBoundary.details': 'Error details (dev only)',

  // Empty states

  'empty.search.title': 'No matches',

  'empty.search.description': 'Adjust or clear filters to see more.',

  'empty.data.title': 'Nothing here yet',

  'empty.data.description': 'Create your first flow to get started.',

  'empty.error.title': 'Could not load',

  'empty.error.description': 'Try again in a moment.',

  // Mock mode

  'mockBanner.prefix': 'Demo mode uses local mock data, not engine output. Set',

  'mockBanner.suffix': 'and connect a real backend through the same-origin gateway.',

  'mockBanner.dismiss': 'Dismiss',

  // Status pages

  'notFound.message': 'This page does not exist.',

  'notFound.home': 'Go home',

  'serverError.message': 'The server hit a problem.',

  'serverError.reload': 'Reload',

  'serverError.home': 'Go home',

  // Operate Module - Process Management

  'common.actions': 'Actions',

  'common.detail': 'Details',

  'common.totalItems': '{{total}} items',

  'common.yes': 'Yes',

  'common.no': 'No',

  'common.previous': 'Previous',

  'common.duplicate': 'Duplicate',

  'common.more': 'More',

  // Filters

  'error.exportFailed': 'Could not export',

  // Process extras

  'feedback.helpfulQuestion': 'Was this helpful?',

  'feedback.quickActions': 'Actions',

  'feedback.helpful': 'Helpful',

  'feedback.notHelpful': 'Not helpful',

  'feedback.bookmark': 'Bookmark',

  'feedback.share': 'Share',

  'feedback.likeSuccess': 'Thanks for the feedback',

  'feedback.likeCancelled': 'Like removed',

  'feedback.dislikeSuccess': 'Thanks—we will improve',

  'feedback.dislikeCancelled': 'Dislike removed',

  'feedback.bookmarkAdded': 'Bookmarked',

  'feedback.bookmarkRemoved': 'Bookmark removed',

  'feedback.linkCopied': 'Link copied',

  'feedback.copyFailed': 'Could not copy the link',

  'feedback.shareSuccess': 'Shared',

  'common.download': 'Download',

  // Footer

  'footer.copyright': 'Copyright 2026 Alibaba CompileFlow contributors.',

  'footer.subtitle': 'CompileFlow Workbench—from learning flows to publishing them.',

  // Workspace Page

  'search.trigger': 'Search…',

  'search.placeholder': 'Search examples and local processes…',

  'search.inputLabel': 'Search examples and local processes',

  'search.dialogTitle': 'Global search',

  'search.enterHint': 'Press Enter',
  'search.loading': 'Opening global search…',
  'search.indexing': 'Building search index…',
  'search.sourcesUnavailable': 'Some search sources are temporarily unavailable',

  'search.matchedExamples': 'Examples',

  'search.matchedProcesses': 'Local processes',

  'search.noMatch': 'No matches—try quick links',

  'search.quickNav': 'Quick links',

  'search.submit': 'Search',

  'search.close': 'Close',

  'search.openShortcut': 'Search (⌘K)',

  'search.quickLink.examplesDesc': 'Browse flow examples',

  'search.quickLink.processesDesc': 'Manage process definitions',

  'search.quickLink.monitoringDesc': 'Live runtime metrics',

  'search.quickLink.logsDesc': 'Search execution logs',

  // Workspace messages
} as const

export default enCommon
