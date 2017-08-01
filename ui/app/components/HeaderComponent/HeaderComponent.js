import React from "react";
import {Link} from "react-router";
import fk_logo from "../../assets/fk-prof-logo.svg";
import styles from "./HeaderComponent.css"

export default class Header extends React.Component {
  constructor(props) {
    super(props);
    this.hideNavDrawer = this.hideNavDrawer.bind(this);
  }

  hideNavDrawer() {
    const mdlLayout = document.querySelector(".mdl-layout");
    console.log(mdlLayout);
    mdlLayout.MaterialLayout.toggleDrawer();
  }

  render() {

    const profileLinkClassName = "mdl-navigation__link mdl-typography--font-thin " + (this.props.isPolicyPage ? "" : styles.isActiveLink);
    const policyLinkClassName = "mdl-navigation__link mdl-typography--font-thin " + (this.props.isPolicyPage ? styles.isActiveLink : "");
    return (
      <div className="mdl-layout mdl-js-layout mdl-layout--fixed-header">
        <header className="mdl-layout__header">
          <div className="mdl-layout__header-row">
            <img src={fk_logo} className="mdl-shadow--4dp" style={{height: '70%', margin: '5px', borderRadius: '2px'}}/>
            <span className="mdl-layout-title mdl-layout--middle" style={{ marginLeft: '10px', marginRight: '20px'}}>FK Profiler</span>
            <nav className="mdl-navigation mdl-layout--large-screen-only">
              <Link className={profileLinkClassName}
                    to={loc => ({pathname: '/profiler/', query: ''})}
                    style={{color: "lightgray", textDecoration: "none"}}>
                Profiles
              </Link>
              <Link className={policyLinkClassName}
                    to={loc => ({pathname: '/profiler/policy', query: ''} )}
                    style={{color: "lightgray", textDecoration: "none"}}>
                Policies
              </Link>
            </nav>
          </div>
        </header>
        <div className="mdl-layout-spacer"/>
        <div className="mdl-layout__drawer">
          <span className="mdl-layout-title"><img src={fk_logo} className="mdl-shadow--4dp" style={{width: '18%', borderRadius: '2px'}}/> FK Profiler</span>
          <nav className="mdl-navigation">
            <Link className={profileLinkClassName}
                  to={loc => ({pathname: '/profiler/', query: ''} )} style={{color: "black", textDecoration: "none"}} onClick={this.hideNavDrawer}>
              Profiles
            </Link>
            <Link className={policyLinkClassName}
                  to={loc => ({pathname: '/profiler/policy', query: ''} )} style={{color: "black", textDecoration: "none"}} onClick={this.hideNavDrawer}>
              Policy
            </Link>
          </nav>
        </div>
      </div>
    );
  }
};
