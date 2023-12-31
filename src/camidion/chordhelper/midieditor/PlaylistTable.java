package camidion.chordhelper.midieditor;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.mididevice.MidiSequencerModel;

/**
 * プレイリストビュー（シーケンスリスト）
 */
public class PlaylistTable extends JTable {
	/** ファイル選択ダイアログ（アプレットの場合は使用不可なのでnull） */
	MidiFileChooser midiFileChooser;
	/** BASE64エンコードアクション */
	Action base64EncodeAction;
	/** BASE64ダイアログ */
	public Base64Dialog base64Dialog;
	/** MIDIデバイスダイアログを開くアクション */
	private Action midiDeviceDialogOpenAction;
	/**
	 * 選択されたMIDIシーケンスのテーブルモデルを返します。
	 * @return 選択されたMIDIシーケンスのテーブルモデル（非選択時はnull）
	 */
	private SequenceTrackListTableModel getSelectedSequenceModel() {
		int i = getSelectedRow();
		if( i < 0 ) return null;
		List<SequenceTrackListTableModel> list = getModel().getSequenceModelList();
		return i >= list.size() ? null : list.get(i);
	}
	/**
	 * 行が選択されているときだけイネーブルになるアクション
	 */
	private abstract class SelectedSequenceAction extends AbstractAction implements ListSelectionListener {
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
			setEnabled(getSelectedRow() >= 0);
		}
		private void init(String tooltip) {
			putValue(Action.SHORT_DESCRIPTION, tooltip);
			selectionModel.addListSelectionListener(this);
			setEnebledBySelection();
		}
	}
	/**
	 * プレイリストビューを構築します。
	 * @param model プレイリストデータモデル
	 * @param midiDeviceDialogOpenAction MIDIデバイスダイアログを開くアクション
	 * @param trackListTable トラックリストテーブル（子テーブル）
	 */
	public PlaylistTable(PlaylistTableModel model, Action midiDeviceDialogOpenAction, SequenceTrackListTable trackListTable) {
		super(model);
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		this.midiDeviceDialogOpenAction = midiDeviceDialogOpenAction;
		try {
			midiFileChooser = new MidiFileChooser();
		}
		catch( ExceptionInInitializerError|NoClassDefFoundError|AccessControlException e ) {
			// アプレットの場合、Webクライアントマシンのローカルファイルには
			// アクセスできないので、ファイル選択ダイアログは使用不可。
			midiFileChooser = null;
		}
		// 再生ボタンを埋め込む
		new PlayButtonCellEditor();
		new PositionCellEditor();
		//
		// 文字コード選択をプルダウンにする
		getColumnModel().getColumn(PlaylistTableModel.Column.CHARSET.ordinal())
			.setCellEditor(new DefaultCellEditor(new CharsetComboBox()));
		setAutoCreateColumnsFromModel(false);
		//
		// Base64画面を開くアクションの生成
		base64Dialog = new Base64Dialog(this);
		base64EncodeAction = new AbstractAction("Base64") {
			{
				String tooltip = "Base64 text conversion - Base64テキスト変換";
				putValue(Action.SHORT_DESCRIPTION, tooltip);
			}
			@Override
			public void actionPerformed(ActionEvent e) {
				base64Dialog.setSequenceModel(getSelectedSequenceModel());
				base64Dialog.setVisible(true);
			}
		};
		TableColumnModel colModel = getColumnModel();
		Arrays.stream(PlaylistTableModel.Column.values()).forEach(c->{
			TableColumn tc = colModel.getColumn(c.ordinal());
			tc.setPreferredWidth(c.preferredWidth);
			if( c == PlaylistTableModel.Column.LENGTH ) lengthColumn = tc;
		});
		selectionModel.addListSelectionListener(event->{
			if( event.getValueIsAdjusting() ) return;
			trackListTable.setModel(getSelectedSequenceModel());
			trackListTable.titleLabel.showMidiFileNumber(selectionModel);
		});
	}
	private TableColumn lengthColumn;
	@Override
	public void tableChanged(TableModelEvent event) {
		super.tableChanged(event);
		//
		// タイトルに合計シーケンス長を表示
		if( lengthColumn != null ) {
			int sec = getModel().getSecondLength();
			String title = PlaylistTableModel.Column.LENGTH.title;
			title = String.format(title+" [%02d:%02d]", sec/60, sec%60);
			lengthColumn.setHeaderValue(title);
		}
		// シーケンス削除時など、合計シーケンス長が変わっても
		// 列モデルからではヘッダタイトルが再描画されないことがある。
		// そこで、ヘッダビューから repaint() で突っついて再描画させる。
		JTableHeader th = getTableHeader();
		if( th != null ) th.repaint();
	}
	/** 時間位置を表示し、ダブルクリックによるシーケンサへのロードのみを受け付けるセルエディタ */
	private class PositionCellEditor extends AbstractCellEditor implements TableCellEditor {
		public PositionCellEditor() {
			getColumnModel().getColumn(PlaylistTableModel.Column.POSITION.ordinal()).setCellEditor(this);
		}
		/**
		 * セルをダブルクリックしたときだけ編集モードに入るようにします。
		 * @param e イベント（マウスイベント）
		 * @return 編集可能な場合true
		 */
		@Override
		public boolean isCellEditable(EventObject e) {
			return (e instanceof MouseEvent) && ((MouseEvent)e).getClickCount() == 2;
		}
		@Override
		public Object getCellEditorValue() { return null; }
		/**
		 * 編集モード時のコンポーネントを返すタイミングで
		 * そのシーケンスをシーケンサーにロードしたあと、すぐに編集モードを解除します。
		 * @return 常にnull
		 */
		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column
		) {
			try {
				getModel().loadToSequencer(row);
			} catch (InvalidMidiDataException|IllegalStateException ex) {
				JOptionPane.showMessageDialog(
						table.getRootPane(), ex,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
			}
			fireEditingStopped();
			return null;
		}
	}
	/** 再生ボタンを埋め込んだセルの編集、描画を行うクラスです。 */
	private class PlayButtonCellEditor extends AbstractCellEditor implements TableCellEditor, TableCellRenderer {
		/** 埋め込み用の再生ボタン */
		private JToggleButton playButton = new JToggleButton(getModel().getSequencerModel().getStartStopAction()) {
			{ setMargin(ChordHelperApplet.ZERO_INSETS); }
		};
		/**
		 * 埋め込み用のMIDIデバイス接続ボタン（そのシーケンスをロードしているシーケンサが開いていなかったときに表示）
		 */
		private JButton midiDeviceConnectionButton = new JButton(midiDeviceDialogOpenAction) {
			{ setMargin(ChordHelperApplet.ZERO_INSETS); }
		};
		/**
		 * 再生ボタンを埋め込むセルエディタを構築し、列に対するレンダラ、エディタとして登録します。
		 */
		public PlayButtonCellEditor() {
			TableColumn tc = getColumnModel().getColumn(PlaylistTableModel.Column.PLAY.ordinal());
			tc.setCellRenderer(this);
			tc.setCellEditor(this);
		}
		/**
		 * {@inheritDoc}
		 *
		 * <p>この実装では、クリックしたセルのシーケンスがシーケンサーで再生可能な場合に
		 * trueを返して再生ボタンを押せるようにします。
		 * それ以外のセルについては、新たにシーケンサーへのロードを可能にするため、
		 * ダブルクリックされたときだけtrueを返します。
		 * </p>
		 */
		@Override
		public boolean isCellEditable(EventObject e) {
			// マウスイベントのみを受け付け、それ以外はデフォルトエディタに振る
			if( ! (e instanceof MouseEvent) ) return super.isCellEditable(e);
			//
			// エディタが編集を終了したことをリスナーに通知
			fireEditingStopped();
			//
			// クリックされたセルの行位置を把握（欄外だったら編集不可）
			MouseEvent me = (MouseEvent)e;
			int row = rowAtPoint(me.getPoint());
			if( row < 0 ) return false;
			//
			// シーケンサーにロード済みの場合は、シングルクリックを受け付ける。
			// それ以外は、ダブルクリックのみ受け付ける。
			return getModel().getSequenceModelList().get(row).isOnSequencer() || me.getClickCount() == 2;
		}
		@Override
		public Object getCellEditorValue() { return null; }
		/**
		 * {@inheritDoc}
		 *
		 * <p>この実装では、行の表すシーケンスの状態に応じたボタンを表示します。
		 * それ以外の場合は、新たにそのシーケンスをシーケンサーにロードしますが、
		 * 以降の編集は不可としてnullを返します。
		 * </p>
		 */
		@Override
		public Component getTableCellEditorComponent(
			JTable table, Object value, boolean isSelected, int row, int column
		) {
			fireEditingStopped();
			PlaylistTableModel model = getModel();
			if( model.getSequenceModelList().get(row).isOnSequencer() ) {
				return model.getSequencerModel().getSequencer().isOpen() ? playButton : midiDeviceConnectionButton;
			}
			try {
				model.loadToSequencer(row);
			} catch (InvalidMidiDataException ex) {
				JOptionPane.showMessageDialog(
						table.getRootPane(), ex,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
			}
			return null;
		}
		/**
		 * {@inheritDoc}
		 *
		 * <p>この実装では、行の表すシーケンスの状態に応じたボタンを表示します。
		 * それ以外の場合はデフォルトレンダラーに描画させます。
		 * </p>
		 */
		@Override
		public Component getTableCellRendererComponent(
			JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column
		) {
			PlaylistTableModel model = getModel();
			if( model.getSequenceModelList().get(row).isOnSequencer() ) {
				return model.getSequencerModel().getSequencer().isOpen() ? playButton : midiDeviceConnectionButton;
			}
			return table.getDefaultRenderer(model.getColumnClass(column))
				.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}
	}
	/**
	 * このプレイリスト（シーケンスリスト）が表示するデータを提供するプレイリストモデルを返します。
	 * @return プレイリストモデル
	 */
	@Override
	public PlaylistTableModel getModel() { return (PlaylistTableModel)dataModel; }
    /**
     * {@link #add(List)} を呼び出し、このプレイリストにMIDIファイルを追加します。
     * @param files MIDIファイル
     * @return 追加されたMIDIファイルのインデックス値（先頭が0、追加されなかった場合は-1）
     */
	public int add(File... files) {
		return add(Arrays.asList(files));
	}
	/**
	 * このプレイリストにMIDIファイルを追加します。追加に失敗した場合はダイアログを表示し、
	 * 後続のMIDIファイルが残っていればそれを追加するかどうかをユーザに尋ねます。
	 * @param files MIDIファイルのリスト
	 * @return 追加されたMIDIファイルのインデックス値（先頭が0、追加されなかった場合は-1）
	 */
	public int add(List<File> files) {
		int firstIndex = -1;
		Iterator<File> itr = files.iterator();
		while(itr.hasNext()) {
			File file = itr.next();
			try (FileInputStream in = new FileInputStream(file)) {
				Sequence sequence = MidiSystem.getSequence(in);
				int lastIndex = ((PlaylistTableModel)dataModel).add(sequence, file.getName());
				if( firstIndex < 0 ) firstIndex = lastIndex;
			} catch(IOException|InvalidMidiDataException e) {
				String message = "Could not open as MIDI file "+file+"\n"+e;
				if( ! itr.hasNext() ) {
					JOptionPane.showMessageDialog(
							getRootPane(), message,
							ChordHelperApplet.VersionInfo.NAME,
							JOptionPane.WARNING_MESSAGE);
					break;
				}
				if( JOptionPane.showConfirmDialog(
						getRootPane(),
						message + "\n\nContinue to open next file ?",
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION
				) break;
			} catch(Exception ex) {
				JOptionPane.showMessageDialog(
						getRootPane(), ex, ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
				break;
			}
		}
		return firstIndex;
	}
	/**
	 * 指定されたシーケンスを追加して再生します。
	 * @param sequence 再生するシーケンス
	 * @param charset 文字コード
	 * @return 追加されたシーケンスのインデックス（先頭が 0）
	 * @throws InvalidMidiDataException {@link Sequencer#setSequence(Sequence)} を参照
	 * @throws IllegalStateException MIDIシーケンサデバイスが閉じている場合
	 */
	public int play(Sequence sequence, Charset charset) throws InvalidMidiDataException {
		int index = getModel().play(sequence, charset);
		selectionModel.setSelectionInterval(index, index);
		return index;
	}
	/**
	 * シーケンスを削除するアクション
	 */
	Action deleteSequenceAction = new SelectedSequenceAction(
		"Delete", MidiSequenceEditorDialog.deleteIcon,
		"Delete selected MIDI sequence - 選択した曲をプレイリストから削除"
	) {
		private static final String CONFIRM_MESSAGE =
			"Selected MIDI sequence not saved - delete it from the playlist ?\n" +
			"選択したMIDIシーケンスはまだ保存されていません。プレイリストから削除しますか？";
		@Override
		public void actionPerformed(ActionEvent event) {
			int index = getSelectedRow();
			if( index < 0 ) return;
			PlaylistTableModel model = getModel();
			List<SequenceTrackListTableModel> list = model.getSequenceModelList();
			if( index >= list.size() ) return;
			SequenceTrackListTableModel sequenceModel = list.get(index);
			if( sequenceModel == null ) return;
			if( midiFileChooser != null ) {
				if( sequenceModel.isModified() && JOptionPane.showConfirmDialog(
						((JComponent)event.getSource()).getRootPane(),
						CONFIRM_MESSAGE,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.YES_NO_OPTION,
						JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION
				) return;
			}
			try {
				model.remove(index);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
						((JComponent)event.getSource()).getRootPane(), ex,
						ChordHelperApplet.VersionInfo.NAME,
						JOptionPane.ERROR_MESSAGE);
			}
		}
	};
	/**
	 * ファイル選択ダイアログ（アプレットでは使用不可）
	 */
	class MidiFileChooser extends JFileChooser {
		{ setFileFilter(new FileNameExtensionFilter("MIDI sequence (*.mid)", "mid")); }
		/**
		 * ファイル保存アクション
		 */
		public Action saveMidiFileAction = new SelectedSequenceAction(
			"Save",
			"Save selected MIDI sequence to file - 選択したMIDIシーケンスをファイルに保存"
		) {
			@Override
			public void actionPerformed(ActionEvent event) {
				SequenceTrackListTableModel sequenceModel = getSelectedSequenceModel();
				if( sequenceModel == null ) return;
				String fn = sequenceModel.getFilename();
				if( fn != null && ! fn.isEmpty() ) setSelectedFile(new File(fn));
				JRootPane rootPane = ((JComponent)event.getSource()).getRootPane();
				if( showSaveDialog(rootPane) != JFileChooser.APPROVE_OPTION ) return;
				File f = getSelectedFile();
				if( f.exists() ) {
					fn = f.getName();
					if( JOptionPane.showConfirmDialog(
							rootPane,
							"Overwrite " + fn + " ?\n" + fn + " を上書きしてよろしいですか？",
							ChordHelperApplet.VersionInfo.NAME,
							JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION
					) return;
				}
				try ( FileOutputStream o = new FileOutputStream(f) ) {
					o.write(sequenceModel.getMIDIdata());
					sequenceModel.setModified(false);
				}
				catch( Exception ex ) {
					JOptionPane.showMessageDialog(
							rootPane, ex, ChordHelperApplet.VersionInfo.NAME,
							JOptionPane.ERROR_MESSAGE);
				}
			}
		};
		/**
		 * ファイルを開くアクション
		 */
		public Action openMidiFileAction = new AbstractAction("Open") {
			{ putValue(Action.SHORT_DESCRIPTION, "Open MIDI file - MIDIファイルを開く"); }
			@Override
			public void actionPerformed(ActionEvent event) {
				JRootPane rootPane = ((JComponent)event.getSource()).getRootPane();
				try {
					if( showOpenDialog(rootPane) != JFileChooser.APPROVE_OPTION ) return;
				} catch( HeadlessException ex ) {
					ex.printStackTrace();
					return;
				}
				int firstIndex = PlaylistTable.this.add(getSelectedFile());
				try {
					PlaylistTableModel model = getModel();
					MidiSequencerModel sequencerModel = model.getSequencerModel();
					if( sequencerModel.getSequencer().isRunning() ) return;
					if( firstIndex >= 0 ) {
						model.play(firstIndex);
						selectionModel.setSelectionInterval(firstIndex, firstIndex);
					}
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(
							rootPane, ex, ChordHelperApplet.VersionInfo.NAME,
							JOptionPane.ERROR_MESSAGE);
				}
			}
		};
	};
}