package net.sourceforge.coffea.editors.figures;

import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import net.sourceforge.coffea.uml2.model.IInterfaceService;

/**
 * Figure displaying an interface 
 * @param <I>
 * Type of service for the displayed interface
 */
@SuppressWarnings("restriction")
public class InterfaceFigure<I extends IInterfaceService<?, ?>> 
extends TypeFigure<I> {
	
	/**
	 * Interface icon creation
	 * @return Created icon
	 */
	public static Image createInterfaceIcon() {
		ImageDescriptor img = JavaPluginImages.DESC_OBJS_INTERFACE;
		return img.createImage();
	}

	/** Class methods figure */
	protected OperationsFigure methodFigure;
	
	/**
	 * Construction of a figure displaying an interface
	 * @param srv
	 * Service for the displayed interface
	 */
	public InterfaceFigure(I srv) {
		super(srv);
		methodFigure = 
			new OperationsFigure(typeService.getOperationsServices());
		ToolbarLayout membersLayout = new ToolbarLayout();
		membersLayout.setMinorAlignment(ToolbarLayout.ALIGN_CENTER);
		membersLayout.setStretchMinorAxis(false);
		membersLayout.setSpacing(4);
		add(methodFigure);
	}
	
	@Override
	protected Image createTypeIcon() {
		return createInterfaceIcon();
	}

}
