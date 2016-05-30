package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Vector;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListSelectionModel;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import camidion.chordhelper.ButtonIcon;
import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.music.ChordProgression;

/**
 * プレイリスト（MIDIシーケンスリスト）のテーブルデータモデル
 */
public class PlaylistTableModel extends AbstractTableModel {
	/**
	 * MIDIシーケンサモデル
	 */
	public MidiSequencerModel sequencerModel;
	/**
	 * 空のトラックリストモデル
	 */
	SequenceTrackListTableModel emptyTrackListTableModel;
	/**
	 * 空のイベントリストモデル
	 */
	TrackEventListTableModel emptyEventListTableModel;
	/**
	 * 選択されているシーケンスのインデックス
	 */
	public ListSelectionModel sequenceListSelectionModel = new DefaultListSelectionModel() {
		{
			setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		}
	};
	/**
	 * 新しいプレイリストのテーブルモデルを構築します。
	 * @param sequencerModel 連携するMIDIシーケンサーモデル
	 */
	public PlaylistTableModel(MidiSequencerModel sequencerModel) {
		this.sequencerModel = sequencerModel;
		sequencerModel.addChangeListener(secondPosition = new SecondPosition());
		sequencerModel.getSequencer().addMetaEventListener(
			new MetaEventListener() {
				/**
				 * {@inheritDoc}
				 *
				 * <p>EOT (End Of Track、type==0x2F) を受信したとき、次の曲へ進みます。
				 * </p>
				 * <p>これは MetaEventListener のための実装なので、多くの場合
				 * Swing EDT ではなく MIDI シーケンサの EDT から起動されます。
				 * Swing EDT とは違うスレッドで動いていた場合は Swing EDT に振り直されます。
				 * </p>
				 */
				@Override
				public void meta(MetaMessage msg) {
					if( msg.getType() == 0x2F ) {
						if( ! SwingUtilities.isEventDispatchThread() ) {
							SwingUtilities.invokeLater(
								new Runnable() {
									@Override
									public void run() { goNext(); }
								}
							);
							return;
						}
						goNext();
					}
				}
			}
		);
		emptyTrackListTableModel = new SequenceTrackListTableModel(this, null, null);
		emptyEventListTableModel = new TrackEventListTableModel(emptyTrackListTableModel, null);
	}
	/**
	 * 次の曲へ進みます。
	 *
	 * <p>リピートモードの場合は同じ曲をもう一度再生、
	 * そうでない場合は次の曲へ進んで再生します。
	 * 次の曲がなければ、そこで停止します。
	 * いずれの場合も曲の先頭へ戻ります。
	 * </p>
	 */
	private void goNext() {
		// とりあえず曲の先頭へ戻る
		sequencerModel.getSequencer().setMicrosecondPosition(0);
		if( (Boolean)toggleRepeatAction.getValue(Action.SELECTED_KEY) || loadNext(1) ) {
			// リピートモードのときはもう一度同じ曲を、
			// そうでない場合は次の曲を再生開始
			sequencerModel.start();
		}
		else {
			// 最後の曲が終わったので、停止状態にする
			sequencerModel.stop();
			// ここでボタンが停止状態に変わったはずなので、
			// 通常であれば再生ボタンが自力で再描画するところだが、
			//
			// セルのレンダラーが描く再生ボタンには効かないようなので、
			// セルを突っついて再表示させる。
			int rowIndex = indexOfSequenceOnSequencer();
			int colIndex = Column.PLAY.ordinal();
			fireTableCellUpdated(rowIndex, colIndex);
		}
	}
	/**
	 * シーケンスリスト
	 */
	private List<SequenceTrackListTableModel> sequenceList = new Vector<>();
	/**
	 * このプレイリストが保持している {@link SequenceTrackListTableModel} のリストを返します。
	 */
	public List<SequenceTrackListTableModel> getSequenceList() { return sequenceList; }
	/**
	 * 行が選択されているときだけイネーブルになるアクション
	 */
	public abstract class SelectedSequenceAction extends AbstractAction implements ListSelectionListener {
		public SelectedSequenceAction(String name, Icon icon, String tooltip) {
			super(name,icon); init(tooltip);
		}
		public SelectedSequenceAction(String name, String tooltip) {
			super(name); init(tooltip);
		}
		@Override
		public void valueChanged(ListSelectionEvent e) {
			if( e.getValueIsAdjusting() ) return;
			setEnebledBySelection();
		}
		protected void setEnebledBySelection() {
			int index = sequenceListSelectionModel.getMinSelectionIndex();
			setEnabled(index >= 0);
		}
		private void init(String tooltip) {
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			sequenceListSelectionModel.addListSelectionListener(this);
			setEnebledBySelection();
		}
	}
	/**
	 * 繰り返し再生ON/OFF切り替えアクション
	 */
	public Action toggleRepeatAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Repeat - 繰り返し再生");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.REPEAT_ICON));
			putValue(SELECTED_KEY, false);
		}
		@Override
		public void actionPerformed(ActionEvent event) { }
	};
	/**
	 * 再生中のシーケンサーの秒位置
	 */
	private class SecondPosition implements ChangeListener {
		private int value = 0;
		/**
		 * 再生中のシーケンサーの秒位置が変わったときに表示を更新します。
		 * @param event 変更イベント
		 */
		@Override
		public void stateChanged(ChangeEvent event) {
			Object src = event.getSource();
			if( src instanceof MidiSequencerModel ) {
				int newValue = ((MidiSequencerModel)src).getValue() / 1000;
				if(value == newValue) return;
				value = newValue;
				int rowIndex = indexOfSequenceOnSequencer();
				fireTableCellUpdated(rowIndex, Column.POSITION.ordinal());
			}
		}
		@Override
		public String toString() {
			return String.format("%02d:%02d", value/60, value%60);
		}
	}
	/**
	 * 曲の先頭または前の曲へ戻るアクション
	 */
	public Action moveToTopAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION,
				"Move to top or previous song - 曲の先頭または前の曲へ戻る"
			);
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.TOP_ICON));
		}
		public void actionPerformed(ActionEvent event) {
			if( sequencerModel.getSequencer().getTickPosition() <= 40 ) loadNext(-1);
			sequencerModel.setValue(0);
		}
	};
	/**
	 * 次の曲へ進むアクション
	 */
	public Action moveToBottomAction = new AbstractAction() {
		{
			putValue(SHORT_DESCRIPTION, "Move to next song - 次の曲へ進む");
			putValue(LARGE_ICON_KEY, new ButtonIcon(ButtonIcon.BOTTOM_ICON));
		}
		public void actionPerformed(ActionEvent event) {
			if(loadNext(1)) sequencerModel.setValue(0);
		}
	};

	/**
	 * 列の列挙型
	 */
	public enum Column {
		/** MIDIシーケンスの番号 */
		NUMBER("No.", Integer.class, 20),
		/** 再生ボタン */
		PLAY("Play/Stop", String.class, 60) {
			@Override
			public boolean isCellEditable() { return true; }
		},
		/** 再生中の時間位置（分：秒） */
		POSITION("Position", String.class, 60) {
			@Override
			public boolean isCellEditable() { return true; } // ダブルクリックだけ有効
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.isOnSequencer()
					? sequenceModel.sequenceListTableModel.secondPosition : "";
			}
		},
		/** シーケンスの時間長（分：秒） */
		LENGTH("Length", String.class, 80) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				long usec = sequenceModel.getSequence().getMicrosecondLength();
				int sec = (int)( (usec < 0 ? usec += 0x100000000L : usec) / 1000L / 1000L );
				return String.format( "%02d:%02d", sec/60, sec%60 );
			}
		},
		/** ファイル名 */
		FILENAME("Filename", String.class, 100) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				String filename = sequenceModel.getFilename();
				return filename == null ? "" : filename;
			}
		},
		/** 変更済みフラグ */
		MODIFIED("Modified", Boolean.class, 50) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.isModified();
			}
		},
		/** シーケンス名（最初のトラックの名前） */
		NAME("Sequence name", String.class, 250) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				String name = sequenceModel.toString();
				return name == null ? "" : name;
			}
		},
		/** 文字コード */
		CHARSET("CharSet", String.class, 80) {
			@Override
			public boolean isCellEditable() { return true; }
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.charset;
			}
		},
		/** タイミング解像度 */
		RESOLUTION("Resolution", Integer.class, 60) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.getSequence().getResolution();
			}
		},
		/** トラック数 */
		TRACKS("Tracks", Integer.class, 40) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				return sequenceModel.getSequence().getTracks().length;
			}
		},
		/** タイミング分割形式 */
		DIVISION_TYPE("DivType", String.class, 50) {
			@Override
			public Object getValueOf(SequenceTrackListTableModel sequenceModel) {
				float divType = sequenceModel.getSequence().getDivisionType();
				if( divType == Sequence.PPQ ) return "PPQ";
				else if( divType == Sequence.SMPTE_24 ) return "SMPTE_24";
				else if( divType == Sequence.SMPTE_25 ) return "SMPTE_25";
				else if( divType == Sequence.SMPTE_30 ) return "SMPTE_30";
				else if( divType == Sequence.SMPTE_30DROP ) return "SMPTE_30DROP";
				else return "[Unknown]";
			}
		};
		String title;
		Class<?> columnClass;
		int preferredWidth;
		/**
		 * 列の識別子を構築します。
		 * @param title 列のタイトル
		 * @param columnClass 列のクラス
		 * @param perferredWidth 列の適切な幅
		 */
		private Column(String title, Class<?> columnClass, int preferredWidth) {
			this.title = title;
			this.columnClass = columnClass;
			this.preferredWidth = preferredWidth;
		}
		public boolean isCellEditable() { return false; }
		public Object getValueOf(SequenceTrackListTableModel sequenceModel) { return ""; }
	}

	@Override
	public int getRowCount() { return sequenceList.size(); }
	@Override
	public int getColumnCount() { return Column.values().length; }
	@Override
	public String getColumnName(int column) { return Column.values()[column].title; }
	@Override
	public Class<?> getColumnClass(int column) { return Column.values()[column].columnClass; }
	@Override
	public boolean isCellEditable(int row, int column) {
		return Column.values()[column].isCellEditable();
	}
	/** 再生中のシーケンサーの秒位置 */
	private SecondPosition secondPosition;
	@Override
	public Object getValueAt(int row, int column) {
		PlaylistTableModel.Column c = Column.values()[column];
		return c == Column.NUMBER ? row : c.getValueOf(sequenceList.get(row));
	}
	@Override
	public void setValueAt(Object val, int row, int column) {
		switch(Column.values()[column]) {
		case FILENAME:
			// ファイル名の変更
			sequenceList.get(row).setFilename((String)val);
			fireTableCellUpdated(row, column);
			break;
		case NAME:
			// シーケンス名の設定または変更
			if( sequenceList.get(row).setName((String)val) )
				fireTableCellUpdated(row, Column.MODIFIED.ordinal());
			fireTableCellUpdated(row, column);
			break;
		case CHARSET:
			// 文字コードの変更
			SequenceTrackListTableModel seq = sequenceList.get(row);
			seq.charset = Charset.forName(val.toString());
			fireTableCellUpdated(row, column);
			// シーケンス名の表示更新
			fireTableCellUpdated(row, Column.NAME.ordinal());
			// トラック名の表示更新
			seq.fireTableDataChanged();
		default:
			break;
		}
	}
	/**
	 * このプレイリストに読み込まれた全シーケンスの合計時間長を返します。
	 * @return 全シーケンスの合計時間長 [秒]
	 */
	public int getTotalSeconds() {
		int total = 0;
		long usec;
		for( SequenceTrackListTableModel m : sequenceList ) {
			usec = m.getSequence().getMicrosecondLength();
			total += (int)( (usec < 0 ? usec += 0x100000000L : usec)/1000L/1000L );
		}
		return total;
	}
	/**
	 * 未保存の修正内容を持つシーケンスがあるか調べます。
	 * @return 未保存の修正内容を持つシーケンスがあればtrue
	 */
	public boolean isModified() {
		for( SequenceTrackListTableModel m : sequenceList ) if( m.isModified() ) return true;
		return false;
	}
	/**
	 * 選択したシーケンスに未保存の修正内容があることを記録します。
	 * @param selModel 選択状態
	 * @param isModified 未保存の修正内容があるときtrue
	 */
	public void setModified(boolean isModified) {
		int minIndex = sequenceListSelectionModel.getMinSelectionIndex();
		int maxIndex = sequenceListSelectionModel.getMaxSelectionIndex();
		for( int i = minIndex; i <= maxIndex; i++ ) {
			if( sequenceListSelectionModel.isSelectedIndex(i) ) {
				sequenceList.get(i).setModified(isModified);
				fireTableCellUpdated(i, Column.MODIFIED.ordinal());
			}
		}
	}
	/**
	 * 選択されたMIDIシーケンスのテーブルモデルを返します。
	 * @return 選択されたMIDIシーケンスのテーブルモデル（非選択時はnull）
	 */
	public SequenceTrackListTableModel getSelectedSequenceModel() {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return null;
		int selectedIndex = sequenceListSelectionModel.getMinSelectionIndex();
		if( selectedIndex >= sequenceList.size() ) return null;
		return sequenceList.get(selectedIndex);
	}
	/**
	 * 指定されたシーケンスが修正されたことを通知します。
	 * @param sequenceTableModel MIDIシーケンスモデル
	 */
	public void fireSequenceModified(SequenceTrackListTableModel sequenceTableModel) {
		int index = sequenceList.indexOf(sequenceTableModel);
		if( index < 0 ) return;
		sequenceTableModel.setModified(true);
		fireTableRowsUpdated(index, index);
	}
	/**
	 * 指定されている選択範囲のシーケンスが変更されたことを通知します。
	 * 更新済みフラグをセットし、選択されたシーケンスの全ての列を再表示します。
	 */
	public void fireSelectedSequenceModified() {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return;
		int minIndex = sequenceListSelectionModel.getMinSelectionIndex();
		int maxIndex = sequenceListSelectionModel.getMaxSelectionIndex();
		for( int index = minIndex; index <= maxIndex; index++ ) {
			sequenceList.get(index).setModified(true);
		}
		fireTableRowsUpdated(minIndex, maxIndex);
	}
	/**
	 * ファイル名つきのMIDIシーケンスを追加します。
	 * @param sequence MIDIシーケンス
	 * @param filename ファイル名
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 */
	public int addSequence(Sequence sequence, String filename) {
		sequenceList.add( new SequenceTrackListTableModel(this, sequence, filename) );
		int lastIndex = sequenceList.size() - 1;
		fireTableRowsInserted(lastIndex, lastIndex);
		return lastIndex;
	}
	/**
	 * デフォルトの内容でMIDIシーケンスを作成して追加します。
	 * @return 追加されたMIDIシーケンスのインデックス（先頭が 0）
	 */
	public int addSequence() {
		Sequence seq = (new ChordProgression()).toMidiSequence();
		return seq == null ? -1 : addSequence(seq,null);
	}
	/**
	 * バイト列とファイル名からMIDIシーケンスを追加します。
	 * バイト列が null の場合、空のMIDIシーケンスを追加します。
	 * @param data バイト列
	 * @param filename ファイル名
	 * @return 追加先インデックス（先頭が 0）
	 * @throws IOException ファイル読み込みに失敗した場合
	 * @throws InvalidMidiDataException MIDIデータが正しくない場合
	 */
	public int addSequence(byte[] data, String filename) throws IOException, InvalidMidiDataException {
		if( data == null ) return addSequence();
		int lastIndex;
		try (InputStream in = new ByteArrayInputStream(data)) {
			lastIndex = addSequence(MidiSystem.getSequence(in), filename);
		} catch( IOException|InvalidMidiDataException e ) {
			throw e;
		}
		sequenceListSelectionModel.setSelectionInterval(lastIndex, lastIndex);
		return lastIndex;
	}
	/**
	 * MIDIファイルを追加します。
	 * ファイルが null の場合、デフォルトのMIDIシーケンスを追加します。
	 * @param midiFile MIDIファイル
	 * @return 追加先インデックス（先頭が 0）
	 * @throws InvalidMidiDataException ファイル内のMIDIデータが正しくない場合
	 * @throws IOException ファイル入出力に失敗した場合
	 */
	public int addSequence(File midiFile) throws InvalidMidiDataException, IOException {
		if( midiFile == null ) return addSequence();
		int lastIndex;
		try (FileInputStream in = new FileInputStream(midiFile)) {
			Sequence seq = MidiSystem.getSequence(in);
			String filename = midiFile.getName();
			lastIndex = addSequence(seq, filename);
		} catch( InvalidMidiDataException|IOException e ) {
			throw e;
		}
		return lastIndex;
	}
	/**
	 * MIDIシーケンスを追加し、再生されていなかった場合は追加したシーケンスから再生を開始します。
	 * @param sequence MIDIシーケンス
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	public int addSequenceAndPlay(Sequence sequence) throws InvalidMidiDataException {
		int lastIndex = addSequence(sequence,"");
		if( ! sequencerModel.getSequencer().isRunning() ) {
			loadToSequencer(lastIndex);
			sequencerModel.start();
		}
		return lastIndex;
	}
	/**
	 * 複数のMIDIファイルを追加します。
	 * @param fileList 追加するMIDIファイルのリスト
	 * @return 追加先の最初のインデックス（先頭が 0、追加されなかった場合は -1）
	 * @throws InvalidMidiDataException ファイル内のMIDIデータが正しくない場合
	 * @throws IOException ファイル入出力に失敗した場合
	 */
	public int addSequences(List<File> fileList) throws InvalidMidiDataException, IOException {
		int firstIndex = -1;
		for( File file : fileList ) {
			int lastIndex = addSequence(file);
			if( firstIndex == -1 ) firstIndex = lastIndex;
		}
		return firstIndex;
	}
	/**
	 * URLから読み込んだMIDIシーケンスを追加します。
	 * @param midiFileUrl MIDIファイルのURL
	 * @return 追加先インデックス（先頭が 0、失敗した場合は -1）
	 * @throws URISyntaxException URLの形式に誤りがある場合
	 * @throws IOException 入出力に失敗した場合
	 * @throws InvalidMidiDataException MIDIデータが正しくない場合
	 */
	public int addSequenceFromURL(String midiFileUrl)
		throws URISyntaxException, IOException, InvalidMidiDataException
	{
		URL url = (new URI(midiFileUrl)).toURL();
		return addSequence(MidiSystem.getSequence(url), url.getFile().replaceFirst("^.*/",""));
	}

	/**
	 * 選択されたシーケンスを除去します。
	 * 除去されたシーケンスがシーケンサーにロード済みの場合、アンロードします。
	 *
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	public void removeSelectedSequence() throws InvalidMidiDataException {
		if( sequenceListSelectionModel.isSelectionEmpty() ) return;
		int selectedIndex = sequenceListSelectionModel.getMinSelectionIndex();
		SequenceTrackListTableModel removedSequence = sequenceList.remove(selectedIndex);
		if( removedSequence.isOnSequencer() ) sequencerModel.setSequenceTrackListTableModel(null);
		fireTableRowsDeleted(selectedIndex, selectedIndex);
	}
	/**
	 * テーブル内の指定したインデックス位置にあるシーケンスをシーケンサーにロードします。
	 * インデックスに -1 を指定するとアンロードされます。
	 * 変更点がある場合、リスナー（テーブルビュー）に通知します。
	 *
	 * @param newRowIndex ロードするシーケンスのインデックス位置、アンロードするときは -1
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	public void loadToSequencer(int newRowIndex) throws InvalidMidiDataException {
		SequenceTrackListTableModel oldSeq = sequencerModel.getSequenceTrackListTableModel();
		SequenceTrackListTableModel newSeq = (newRowIndex < 0 ? null : sequenceList.get(newRowIndex));
		if( oldSeq == newSeq ) return;
		sequencerModel.setSequenceTrackListTableModel(newSeq);
		int columnIndices[] = {
			Column.PLAY.ordinal(),
			Column.POSITION.ordinal(),
		};
		if( oldSeq != null ) {
			int oldRowIndex = sequenceList.indexOf(oldSeq);
			for( int columnIndex : columnIndices ) fireTableCellUpdated(oldRowIndex, columnIndex);
		}
		if( newSeq != null ) {
			for( int columnIndex : columnIndices ) fireTableCellUpdated(newRowIndex, columnIndex);
		}
	}
	/**
	 * 現在シーケンサにロードされているシーケンスのインデックスを返します。
	 * ロードされていない場合は -1 を返します。
	 * @return 現在シーケンサにロードされているシーケンスのインデックス
	 */
	public int indexOfSequenceOnSequencer() {
		return sequenceList.indexOf(sequencerModel.getSequenceTrackListTableModel());
	}
	/**
	 * 引数で示された数だけ次へ進めたシーケンスをロードします。
	 * @param offset 進みたいシーケンス数
	 * @return true:ロード成功、false:これ以上進めない
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 */
	public boolean loadNext(int offset) {
		int loadedIndex = indexOfSequenceOnSequencer();
		int index = (loadedIndex < 0 ? 0 : loadedIndex + offset);
		if( index < 0 || index >= sequenceList.size() ) return false;
		try {
			loadToSequencer(index);
		} catch (InvalidMidiDataException ex) {
			ex.printStackTrace();
			JOptionPane.showMessageDialog(
				null, ex.getMessage(),
				ChordHelperApplet.VersionInfo.NAME, JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}
}
