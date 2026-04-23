import React, { useState } from 'react';
import { StatusBar } from 'react-native';
import { TamaguiProvider } from 'tamagui';
import tamaguiConfig from './tamagui.config';
import { LandingPage } from './src/components/LandingPage';
import { MobileExecution } from './src/components/MobileExecution';

function App(): React.JSX.Element {
  const [currentScreen, setCurrentScreen] = useState<'landing' | 'execution'>('landing');

  return (
    <TamaguiProvider config={tamaguiConfig} defaultTheme="dark">
      <StatusBar barStyle="light-content" backgroundColor="#000000" />
      {currentScreen === 'landing' ? (
        <LandingPage onInitialize={() => setCurrentScreen('execution')} />
      ) : (
        <MobileExecution onBack={() => setCurrentScreen('landing')} />
      )}
    </TamaguiProvider>
  );
}

export default App;
