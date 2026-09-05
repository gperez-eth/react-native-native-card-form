Pod::Spec.new do |s|
  s.name           = 'ReactNativeNativeCardForm'
  s.version        = '0.1.0-alpha.0'
  s.summary        = 'Composable React Native card form with private native inputs'
  s.description    = 'React-owned card form layout with native-only sensitive inputs.'
  s.author         = { 'Guillermo Pérez' => 'gperez-eth' }
  s.homepage       = 'https://github.com/gperez-eth/react-native-native-card-form'
  s.license        = { :type => 'MIT', :file => '../LICENSE' }
  s.platforms      = { :ios => '15.1' }
  s.source         = { :git => 'https://github.com/gperez-eth/react-native-native-card-form.git', :tag => "v#{s.version}" }
  s.source_files   = '*.{h,m,swift}'
  s.dependency 'ExpoModulesCore'
  s.dependency 'StripePayments', '~> 24.25.0'
  s.swift_version  = '5.0'
end
