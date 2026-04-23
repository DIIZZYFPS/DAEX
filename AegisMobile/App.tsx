import React, { useState, useCallback } from 'react';
import { StatusBar } from 'react-native';
import { TamaguiProvider } from 'tamagui';
import tamaguiConfig from './tamagui.config';
import { LandingPage } from './src/components/LandingPage';
import { MobileExecution } from './src/components/MobileExecution';
import { llamaService } from './src/services/llamaService';
import { modelManager } from './src/services/modelManager';
import { ModelStatus } from './src/hooks/useAegisInference';

function App(): React.JSX.Element {
  const [currentScreen, setCurrentScreen] = useState<'landing' | 'execution'>('landing');
  const [modelStatus, setModelStatus] = useState<ModelStatus>('not_downloaded');
  const [downloadProgress, setDownloadProgress] = useState<number>(0);

  const handleInitialize = useCallback(async () => {
    try {
      // Check if model is already downloaded
      const isDownloaded = await modelManager.isModelDownloaded();

      if (!isDownloaded) {
        // Download the model
        setModelStatus('downloading');
        setDownloadProgress(0);

        await modelManager.downloadModel((progress) => {
          setDownloadProgress(progress.percent);
        });
      }

      // Load the model
      setModelStatus('loading');
      const modelPath = modelManager.getModelPath();
      await llamaService.initContext(modelPath, false);

      setModelStatus('ready');
      setCurrentScreen('execution');
    } catch (err: any) {
      setModelStatus('error');
      console.error('Initialization failed:', err?.message);
    }
  }, []);

  return (
    <TamaguiProvider config={tamaguiConfig} defaultTheme="dark">
      <StatusBar barStyle="light-content" backgroundColor="#000000" />
      {currentScreen === 'landing' ? (
        <LandingPage
          onInitialize={handleInitialize}
          modelStatus={modelStatus}
          downloadProgress={downloadProgress}
        />
      ) : (
        <MobileExecution onBack={() => setCurrentScreen('landing')} />
      )}
    </TamaguiProvider>
  );
}

export default App;
