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

  'filters.processType': 'Process type',

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

  'pageTitle.learn': 'Process Examples',

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

  'settings.subtitle': 'Customize the workbench and view build information',

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

  'home.eyebrow': 'Visual process orchestration for Java applications',

  'home.title': 'Build clear, reliable business processes',

  'home.subtitle':
    'Visualize business logic for high-performance in-memory execution, durable workflows, and agent orchestration, with versioned deployment and runtime monitoring.',

  'home.start': 'Create your first process',

  'home.docs': 'Explore examples',

  'home.status': 'Status',

  'home.modules': 'Modules',

  'home.version': 'v{{version}} · {{status}}',

  'home.features': 'Learn, build, and operate',

  'home.featuresDesc':
    'Learn common patterns from examples, build your own processes, and manage versions and runtime state.',

  // Features

  'feature.learn.title': 'Learn',

  'feature.learn.desc':
    'Learn common patterns through examples of decisions, parallel execution, waits, and recovery.',

  'feature.learn.action': 'Explore examples',

  'feature.workspace.title': 'Build',

  'feature.workspace.desc':
    'Design processes visually or edit their source, then validate and compile each definition.',

  'feature.workspace.action': 'Open workspace',

  'feature.ops.title': 'Operate',

  'feature.ops.desc':
    'Publish immutable versions, route canary traffic, and identify the process version used for every execution.',

  'feature.ops.action': 'Open operations',

  // Example List

  'theme.light': 'Switch to light mode',

  'theme.dark': 'Switch to dark mode',

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

  'error.pageRenderTitle': 'This page could not be displayed',

  'error.pageRenderDescription': 'Refresh the page and try again.',

  'error.application': 'The application encountered an error. Refresh the page and try again.',

  'errorBoundary.title': 'Something went wrong',

  'errorBoundary.description': 'Try again, or go back home.',

  'errorBoundary.retry': 'Try again',

  'errorBoundary.home': 'Go home',

  'errorBoundary.details': 'Error details (dev only)',

  'errorBoundary.errorLabel': 'Error:',

  'errorBoundary.componentStackLabel': 'Component stack:',

  // Empty states

  'empty.search.title': 'No matches',

  'empty.search.description': 'Adjust or clear filters to see more.',

  'empty.data.title': 'No data yet',

  'empty.data.description': 'Create your first process to get started.',

  'empty.error.title': 'Could not load',

  'empty.error.description': 'Try again in a moment.',

  // Mock mode

  'mockBanner.prefix': 'Demo mode displays local sample data rather than engine output. Set',

  'mockBanner.suffix': 'and connect to a backend through the same-origin gateway.',

  'mockBanner.dismiss': 'Dismiss',

  // Status pages

  'notFound.message': 'The page you requested could not be found.',

  'notFound.home': 'Go home',

  'serverError.message': 'The server encountered an error.',

  'serverError.reload': 'Reload',

  'serverError.home': 'Go home',

  // Operate Module - Process Management

  'common.actions': 'Actions',

  'common.detail': 'View details',

  'common.totalItems': '{{total}} items',

  'common.yes': 'Yes',

  'common.no': 'No',

  'common.previous': 'Previous',

  'common.duplicate': 'Duplicate',

  'common.more': 'More',

  // Filters

  'error.exportFailed': 'Could not export',

  // Process extras

  'feedback.helpfulQuestion': 'Was this example helpful?',

  'feedback.quickActions': 'Actions',

  'feedback.helpful': 'Helpful',

  'feedback.notHelpful': 'Not helpful',

  'feedback.bookmark': 'Bookmark',

  'feedback.share': 'Share',

  'feedback.likeSuccess': 'Marked as helpful',

  'feedback.likeCancelled': 'Helpful vote removed',

  'feedback.dislikeSuccess': 'Marked as not helpful',

  'feedback.dislikeCancelled': 'Not-helpful vote removed',

  'feedback.bookmarkAdded': 'Bookmarked',

  'feedback.bookmarkRemoved': 'Bookmark removed',

  'feedback.linkCopied': 'Link copied',

  'feedback.copyFailed': 'Could not copy link',

  'feedback.shareSuccess': 'Shared',

  'common.download': 'Download',

  // Footer

  'footer.copyright': 'Copyright 2026 Alibaba CompileFlow contributors.',

  'footer.subtitle': 'Learn, design, publish, and operate processes with CompileFlow Workbench.',

  // Workspace Page

  'search.trigger': 'Search…',

  'search.placeholder': 'Search examples and local processes…',

  'search.inputLabel': 'Search examples and local processes',

  'search.dialogTitle': 'Global search',

  'search.enterHint': 'Press Enter',
  'search.loading': 'Opening global search…',
  'search.indexing': 'Building search index…',
  'search.sourcesUnavailable': 'Some search sources are temporarily unavailable. Try again later.',

  'search.matchedExamples': 'Examples',

  'search.matchedProcesses': 'Local processes',

  'search.noMatch': 'No matches. Try a quick link instead.',

  'search.quickNav': 'Quick links',

  'search.submit': 'Search',

  'search.close': 'Close',

  'search.openShortcut': 'Search (⌘K)',

  'search.quickLink.examplesDesc': 'Browse process examples',

  'search.quickLink.processesDesc': 'Manage process definitions',

  'search.quickLink.monitoringDesc': 'Live runtime metrics',

  'search.quickLink.logsDesc': 'Search execution logs',

  // Workspace messages
} as const

export default enCommon
