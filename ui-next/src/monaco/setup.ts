import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import JsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';

const useLocal =
  typeof window !== 'undefined' &&
  (window as any).conductor?.MONACO_USE_LOCAL === true;

if (useLocal) {
  self.MonacoEnvironment = {
    getWorker(_workerId: string, label: string): Worker {
      switch (label) {
        case 'json':
          return new JsonWorker();
        default:
          return new EditorWorker();
      }
    },
  };

  loader.config({ monaco });
}
