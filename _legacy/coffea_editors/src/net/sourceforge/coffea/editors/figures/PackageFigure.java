package net.sourceforge.coffea.editors.figures;

import java.util.List;

import net.sourceforge.coffea.editors.CoffeaEditorsPlugin;
import net.sourceforge.coffea.uml2.model.IClassService;
import net.sourceforge.coffea.uml2.model.IInterfaceService;
import net.sourceforge.coffea.uml2.model.IPackageService;
import net.sourceforge.coffea.uml2.model.ITypeService;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;

/** Figure displaying a package */
@SuppressWarnings("restriction")
public class PackageFigure<P extends IPackageService> extends Figure {

	/**
	 * Package icon creation
	 * @return Created icon
	 */
	public static Image createPackageIcon() {
		ImageDescriptor img = JavaPluginImages.DESC_OBJS_PACKAGE;
		return img.createImage();
	}

	/** Figure background color */
	protected Color backgroundColor;

	/** Service for the displayed package */
	protected P packageService;

	/** Flag extending the display of the package content */
	protected boolean extendedDisplay;

	/**
	 * Construction of a figure displaying a package
	 * @param pck
	 * Service for the displayed package
	 */
	public PackageFigure(P pck) {
		if(pck == null) {
			throw new IllegalArgumentException(
					"Missing package service"
			);
		}
		extendedDisplay = 
			CoffeaEditorsPlugin.getDefault().isSelectionDetailed();
		packageService = pck;
		
		ToolbarLayout layout = new ToolbarLayout();
		/*
		layout.setMinorAlignment(ToolbarLayout.ALIGN_CENTER);
		layout.setStretchMinorAxis(false);
		layout.setSpacing(2);
		*/
		layout.setSpacing(6);
		// setLayoutManager(new FlowLayout(false));
		setLayoutManager(layout);
		setBorder(new LineBorder(ColorConstants.black,1));
		backgroundColor = new Color(null,255,255,206);
		setBackgroundColor(backgroundColor);
		setOpaque(true);
		String name = packageService.getSimpleName();
		
		Label nameLabel = new Label(name, createPackageIcon());
		add(nameLabel);
		List<ITypeService<?, ?>> typeServices = 
			packageService.getTypesServices();
		
		ToolbarLayout membersLayout = new ToolbarLayout();
		membersLayout.setMinorAlignment(ToolbarLayout.ALIGN_CENTER);
		membersLayout.setStretchMinorAxis(false);
		membersLayout.setSpacing(4);
		
		Figure membersFigure = new Figure();
		membersFigure.setLayoutManager(membersLayout);
		membersFigure.setBorder(new CompartmentFigureBorder());
		for(ITypeService<?, ?> typeSrv : typeServices) {
			if(extendedDisplay) {
				if(typeSrv instanceof IClassService) {
					IClassService<?, ?> classSrv = 
						(IClassService<?, ?>)typeSrv;
					membersFigure.add(new ClassFigure(classSrv));
				}
				else if(typeSrv instanceof IInterfaceService) {
					IInterfaceService<?, ?> interfaceSrv = 
						(IInterfaceService<?, ?>)typeSrv;
					membersFigure.add(
							new InterfaceFigure<IInterfaceService<?, ?>>(
									interfaceSrv
							)
					);
				}
			}
			else {
				if(typeSrv instanceof IClassService) {
					Label classLabel = 
						new Label(
								typeSrv.getSimpleName(), 
								ClassFigure.createClassIcon()
						);
					membersFigure.add(classLabel);
				}
				else if(typeSrv instanceof IInterfaceService) {
					Label interfaceLabel = 
						new Label(
								typeSrv.getSimpleName(), 
								InterfaceFigure.createInterfaceIcon()
						);
					membersFigure.add(interfaceLabel);
				}
				else {
					Label label = new Label(typeSrv.getSimpleName());
					membersFigure.add(label);
				}
			}
		}
		add(membersFigure);
	}
}