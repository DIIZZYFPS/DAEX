import 'react-native-gesture-handler';
import 'react-native-reanimated';
import 'react-native-worklets';
import React, { useState, useCallback } from 'react';
import { StatusBar } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { enableLayoutAnimations } from 'react-native-reanimated';
import { TamaguiProvider } from 'tamagui';
import tamaguiConfig from './tamagui.config';
import { LandingPage } from './src/components/LandingPage';
import { MobileExecution } from './src/components/MobileExecution';
import { ModelSelectorModal } from './src/components/ModelSelectorModal';
import { llamaService } from './src/services/llamaService';
import { modelManager } from './src/services/modelManager';
import { MODEL_BANK } from './src/services/modelBank';
import type { Model } from './src/services/modelBank';
import { ModelStatus } from './src/hooks/useDaexInference';

enableLayoutAnimations(true);


function App(): React.JSX.Element {
  const [currentScreen, setCurrentScreen] = useState<'landing' | 'execution'>('landing');
  const [modelStatus, setModelStatus] = useState<ModelStatus>('not_downloaded');
  const [downloadProgress, setDownloadProgress] = useState<number>(0);
  const [selectedModel, setSelectedModel] = useState<Model>(MODEL_BANK[0]);
  const [selectorVisible, setSelectorVisible] = useState(false);

  const handleInitialize = useCallback(async () => {
    try {
      // Check if the selected model is already downloaded
      const isDownloaded = await modelManager.isModelDownloaded(selectedModel);

      if (!isDownloaded) {
        setModelStatus('downloading');
        setDownloadProgress(0);

        await modelManager.downloadModel(selectedModel, (progress) => {
          setDownloadProgress(progress.percent);
        });
      }

      // Load the model
      setModelStatus('loading');
      const modelPath = modelManager.getModelPath(selectedModel);
      await llamaService.initContext(modelPath, false);

      setModelStatus('ready');
      setCurrentScreen('execution');
    } catch (err: any) {
      setModelStatus('error');
      console.error('Initialization failed:', err?.message);
    }
  }, [selectedModel]);

  const handleModelSelect = useCallback((model: Model) => {
    // Reset status when switching models so the landing page reflects the new choice
    setSelectedModel(model);
    setModelStatus('not_downloaded');
    setDownloadProgress(0);
  }, []);

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <TamaguiProvider config={tamaguiConfig} defaultTheme="dark">
        <StatusBar barStyle="light-content" backgroundColor="#000000" />
        {currentScreen === 'landing' ? (
          <>
            <LandingPage
              onInitialize={handleInitialize}
              onChangeModel={() => setSelectorVisible(true)}
              modelStatus={modelStatus}
              downloadProgress={downloadProgress}
              selectedModel={selectedModel}
            />
            <ModelSelectorModal
              visible={selectorVisible}
              selectedModel={selectedModel}
              onSelect={handleModelSelect}
              onClose={() => setSelectorVisible(false)}
            />
          </>
        ) : (
          <MobileExecution
            onBack={() => setCurrentScreen('landing')}
            selectedModel={selectedModel}
          />
        )}
      </TamaguiProvider>
    </GestureHandlerRootView>
  );
}

export default App;
