import React from "react";
import {Link} from "react-router";
import fk_logo from "../../assets/fk-prof-logo.svg";


export default class Header extends React.Component  {
  constructor(props){
    super(props);
    this.hideNavDrawer = this.hideNavDrawer.bind(this);
  }

  hideNavDrawer(){
    console.log((this.refs.navDrawer));
    // this.navDrawer.toggleDrawer();
  }

  render() {
    return (
      <div className="mdl-layout mdl-js-layout mdl-layout--fixed-header">
        <header className="mdl-layout__header"
                style={{backgroundColor: this.props.isPolicyPage ? '#898984' : 'rgb(63,81,181)'}}>
          <div className="mdl-layout__header-row ">
            <span className="mdl-layout-title">Flipkart Profiler</span>
            <div className="mdl-layout-spacer"/>
            <nav className="mdl-navigation mdl-layout--large-screen-only">
              <Link className="mdl-navigation__link mdl-typography--text-uppercase"
                    to={loc => ({pathname: '/profiler/', query: ''} )} onClick={this.hideNavDrawer}
                    style={{color: "white", textDecoration: "none"}}>
                Profiles
              </Link>
              <Link className="mdl-navigation__link mdl-typography--text-uppercase"
                    to={loc => ({pathname: '/profiler/policy', query: ''} )}
                    style={{color: "white", textDecoration: "none"}}>
                Policy
              </Link>
            </nav>
          </div>
        </header>
        <div className="mdl-layout__drawer" ref={(drawer) => { this.navDrawer = drawer; }} >
          <span className="mdl-layout-title"><img src={fk_logo} style={{width: '20%'}}/> FK Profiler</span>
          <nav className="mdl-navigation">
            <Link className="mdl-navigation__link mdl-typography--text-uppercase"
                  to={loc => ({pathname: '/profiler/', query: ''} )} onClick={this.hideNavDrawer}>
              Profiles
            </Link>
            <Link className="mdl-navigation__link mdl-typography--text-uppercase"
                  to={loc => ({pathname: '/profiler/policy', query: ''} )}>
              Policy
            </Link>
          </nav>
        </div>
      </div>
    );
  }
};

//
// <div className="mdl-layout mdl-js-layout mdl-layout--fixed-header">
//   <header className="mdl-layout__header">
//
//     <div className="mdl-layout__header-row" style={{backgroundColor: this.props.isPolicyPage ? '#898984': 'rgb(63,81,181)'}}>
//       <img src={fk_logo} style={{width: '4%', padding: '1em'}}/>
//       <Link to={loc => ({ pathname: '/profiler', query: ''})} style={{ color: "white", textDecoration : "none"}}>
//         <span className="mdl-layout-title" >Flipkart Profiler</span>
//       </Link>
//       <div className="mdl-layout-spacer"/>
//       { !this.props.isPolicyPage &&
//       <Link to={loc => ({pathname: '/profiler/policy', query: ''})}>
//         <button className="mdl-button mdl-js-button mdl-button--icon" style={{color: "white"}}>
//           <i className="material-icons">settings</i>
//         </button>
//       </Link>
//       }
//     </div>
//   </header>
// </div>
