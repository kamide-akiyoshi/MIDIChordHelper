package camidion.chordhelper.mididevice;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * MIDIデバイスを{@link MidiDeviceInOutType}で分類して参照できるようにするツリーモデル
 */
public class MidiDeviceTreeModel implements TreeModel {

	private List<MidiDeviceModel> deviceModelList;
	public List<MidiDeviceModel> getDeviceModelList() { return deviceModelList; }
	public void setDeviceModelList(List<MidiDeviceModel> deviceModelList) {
		group.clear();
		this.deviceModelList = deviceModelList;
		createGroup();
		fireTreeStructureChanged(this, null, null, null);
	}

	private Map<MidiDeviceInOutType, List<MidiDeviceModel>> group = new EnumMap<>(MidiDeviceInOutType.class);
	private void createGroup() {
		for(MidiDeviceInOutType ioType : MidiDeviceInOutType.values()) {
			if( ioType != MidiDeviceInOutType.MIDI_NONE ) group.put(ioType, new ArrayList<>());
		}
		for( MidiDeviceModel m : deviceModelList ) group.get(m.getInOutType()).add(m);
	}
	public Map<MidiDeviceInOutType, List<MidiDeviceModel>> getGroup() { return group; }

	public MidiDeviceTreeModel(List<MidiDeviceModel> deviceModelList) {
		this.deviceModelList = deviceModelList;
		createGroup();
	}

	@Override
	public String toString() { return "MIDI devices"; }
	@Override
	public Object getRoot() { return this; }
	@Override
	public int getChildCount(Object parent) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values().length - 1;
		if( parent instanceof MidiDeviceInOutType ) return group.get(parent).size();
		return 0;
	}
	@Override
	public Object getChild(Object parent, int index) {
		if( parent == getRoot() ) return MidiDeviceInOutType.values()[index + 1];
		if( parent instanceof MidiDeviceInOutType ) return group.get(parent).get(index);
		return null;
	}
	@Override
	public int getIndexOfChild(Object parent, Object child) {
		if( parent == getRoot() ) return ((MidiDeviceInOutType)child).ordinal() - 1;
		if( parent instanceof MidiDeviceInOutType ) return group.get(parent).indexOf(child);
		return -1;
	}
	@Override
	public boolean isLeaf(Object node) { return node instanceof MidiDeviceModel; }
	@Override
	public void valueForPathChanged(TreePath path, Object newValue) {}
	//
	private EventListenerList listenerList = new EventListenerList();
	@Override
	public void addTreeModelListener(TreeModelListener listener) {
		listenerList.add(TreeModelListener.class, listener);
	}
	@Override
	public void removeTreeModelListener(TreeModelListener listener) {
		listenerList.remove(TreeModelListener.class, listener);
	}
	protected void fireTreeStructureChanged(Object source, Object[] path, int[] childIndices, Object[] children) {
		Object[] listeners = listenerList.getListenerList();
		for (int i = listeners.length-2; i>=0; i-=2) {
			if (listeners[i]==TreeModelListener.class) {
				((TreeModelListener)listeners[i+1]).treeStructureChanged(
					new TreeModelEvent(source,path,childIndices,children)
				);
			}
		}
	}
}
