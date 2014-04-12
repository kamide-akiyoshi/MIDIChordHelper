package camidion.chordhelper.chordmatrix;

import java.util.EventListener;

/**
 * コードボタンマトリクスのイベントを受信するリスナー
 */
public interface ChordMatrixListener extends EventListener {
	void chordChanged();
	void keySignatureChanged();
}
