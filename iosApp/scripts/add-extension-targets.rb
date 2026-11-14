# Adds the Share Extension, the widget extension and the Swift test bundle to iosApp.xcodeproj, with the App Group
# entitlements, the bundled fonts, the embed phase and a shared "iosApp" scheme whose Test action runs StashTests.
# Run once, from the repository root, on the project as it was before these targets existed:
#   ruby iosApp/scripts/add-extension-targets.rb
# Needs the xcodeproj gem (1.27.0). Sources live in synchronized folders, so adding a file later needs no re-run.

require 'xcodeproj'

PROJECT = File.expand_path('../iosApp.xcodeproj', __dir__)
DEPLOYMENT_TARGET = '18.2'
FONTS = '../core/resources/src/commonMain/composeResources/font'
Sync = Xcodeproj::Project::Object::PBXFileSystemSynchronizedRootGroup
Exceptions = Xcodeproj::Project::Object::PBXFileSystemSynchronizedBuildFileExceptionSet

project = Xcodeproj::Project.open(PROJECT)
abort 'ShareExtension already exists: the script was applied before' if project.targets.any? { |t| t.name == 'ShareExtension' }
app = project.targets.find { |t| t.name == 'iosApp' }

# A synchronized folder compiled into `targets`; `excluded` files stay out of them, `also` adds single files to others.
def folder(project, path, targets, excluded: [], also: {})
  group = project.new(Sync)
  group.path = path
  group.source_tree = '<group>'
  project.main_group.children.insert(project.main_group.children.index(project.products_group), group)
  targets.each { |t| t.file_system_synchronized_groups << group }
  exceptions = targets.map { |t| [t, excluded] }.reject { |_, files| files.empty? } + also.to_a
  exceptions.each do |target, files|
    set = project.new(Exceptions)
    set.target = target
    set.membership_exceptions = files
    group.exceptions << set
  end
  group
end

def new_target(project, type, name, product, settings)
  target = project.new_target(type, name, :ios, DEPLOYMENT_TARGET, nil, :swift, product)
  # new_target links Foundation explicitly; Swift imports it on its own.
  target.frameworks_build_phase.files.each { |f| f.file_ref.remove_from_project; f.remove_from_project }
  target.build_configurations.each do |config|
    config.build_settings.merge!(
      'SWIFT_VERSION' => '5.0',
      'TARGETED_DEVICE_FAMILY' => '1,2',
      'CODE_SIGN_STYLE' => 'Automatic',
      'DEVELOPMENT_TEAM' => '${TEAM_ID}',
      'GENERATE_INFOPLIST_FILE' => 'YES',
      'SWIFT_EMIT_LOC_STRINGS' => 'YES',
    ).merge!(settings)
  end
  target
end

def extension(project, name, product, bundle_suffix)
  new_target(project, :app_extension, name, product,
    'PRODUCT_NAME' => product,
    'PRODUCT_BUNDLE_IDENTIFIER' => "com.techgv.stash.#{bundle_suffix}",
    'INFOPLIST_FILE' => "#{name}/Info.plist",
    'INFOPLIST_KEY_CFBundleDisplayName' => 'Stash',
    'CODE_SIGN_ENTITLEMENTS' => "#{name}/#{name}.entitlements",
    'APPLICATION_EXTENSION_API_ONLY' => 'YES',
    'SKIP_INSTALL' => 'YES',
    'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks'])
end

share = extension(project, 'ShareExtension', 'ShareExtension', 'ShareExtension')
widget = extension(project, 'WidgetExtension', 'StashWidget', 'Widget')
tests = new_target(project, :unit_test_bundle, 'StashTests', 'StashTests',
  'PRODUCT_NAME' => '$(TARGET_NAME)',
  'PRODUCT_BUNDLE_IDENTIFIER' => 'com.techgv.stash.StashTests')

folder(project, 'ShareExtension', [share], excluded: %w[Info.plist ShareExtension.entitlements],
  also: { tests => %w[InboxWriter.swift SharedLink.swift] })
folder(project, 'WidgetExtension', [widget], excluded: %w[Info.plist WidgetExtension.entitlements],
  also: { tests => %w[WidgetSnapshot.swift] })
folder(project, 'ExtensionSupport', [share, widget])
folder(project, 'StashTests', [tests])

# The extensions use the app's bundled fonts, referenced in place.
fonts = project.main_group.new_group('Fonts', FONTS)
{ share => %w[lora_bold.ttf inter_regular.ttf inter_semibold.ttf],
  widget => %w[playfair_bold_italic.ttf lora_semibold.ttf inter_regular.ttf inter_bold.ttf] }.each do |target, files|
  files.each do |file|
    ref = fonts.files.find { |f| f.path == file } || fonts.new_file(file)
    target.resources_build_phase.add_file_reference(ref)
  end
end

app.build_configurations.each do |config|
  config.build_settings['CODE_SIGN_ENTITLEMENTS'] = 'iosApp/iosApp.entitlements'
  config.build_settings['ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES'] = 'AppIcon-Night AppIcon-Parchment AppIcon-Ink'
end
app_folder = project.main_group.children.find { |g| g.is_a?(Sync) && g.path == 'iosApp' }
app_folder.exceptions.find { |e| e.target == app }.membership_exceptions << 'iosApp.entitlements'

embed = app.new_copy_files_build_phase('Embed Foundation Extensions')
embed.symbol_dst_subfolder_spec = :plug_ins
[share, widget].each do |target|
  app.add_dependency(target)
  embed.add_file_reference(target.product_reference, true).settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
end

project.frameworks_group.remove_from_project

project.save

scheme = Xcodeproj::XCScheme.new
scheme.configure_with_targets(app, tests, launch_target: true)
scheme.save_as(PROJECT, 'iosApp', true)
